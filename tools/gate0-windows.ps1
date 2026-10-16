param(
    [string]$WorkDir = (Join-Path (Get-Location) 'holdfast-gate0')
)

# Gate 0 kit: downloads the exact Forge installer and the two animation libraries, verifies hashes and
# ZIP structure, and installs a Forge dedicated server. It does NOT accept the Minecraft EULA, start the
# server, or load any Holdfast asset. Works in Windows PowerShell 5.1 and PowerShell 7.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
# Windows PowerShell 5.1 on older .NET defaults can refuse modern TLS; harmless elsewhere.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem

$forgeVersion = '1.20.1-47.4.8'
$forgeUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$forgeVersion/forge-$forgeVersion-installer.jar"
$forgeSha256 = '08dce804d25c12726cb8fb65c3041de1be10fb31534dd8cbd500fe1d199a9d57'
# SHA-512 values match the Modrinth API records for these version ids (checked 2026-10-16).
$mods = @(
    @{
        Name = 'geckolib-forge-1.20.1-4.8.4.jar'
        Url = 'https://cdn.modrinth.com/data/8BmcQJ2H/versions/aC5KMoNg/geckolib-forge-1.20.1-4.8.4.jar'
        Sha512 = '443a321a293e3cb92fbbc3c97a38b179a45d1db703cd7bdb123270a9e04e7b7d67d5b06cd8e77a6d4d9139e033e901b9660cccf53ad5f9beaae6d06eae68febf'
    },
    @{
        Name = 'player-animation-lib-forge-1.0.2-rc1+1.20.jar'
        Url = 'https://cdn.modrinth.com/data/gedNE4y2/versions/xe2EVE6q/player-animation-lib-forge-1.0.2-rc1%2B1.20.jar'
        Sha512 = 'cb9f6a3aaa943823a85fdd716ddb3c7623af2b3520926c3a98d170c52d31908894808fb61c52bea39bae622dbab59d50b4bad56f7ca0533a9c3350d412c33729'
    }
)

function Assert-Hash([string]$Path, [string]$Algorithm, [string]$Expected) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) { throw "Hash mismatch for $Path ($Algorithm): got $actual expected $Expected" }
    Write-Host "Verified $Algorithm : $Path"
}

function Assert-Zip([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Missing file: $Path" }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        if ($archive.Entries.Count -eq 0) { throw "Empty archive: $Path" }
        Write-Host "Verified ZIP : $Path ($($archive.Entries.Count) entries, $((Get-Item -LiteralPath $Path).Length) bytes)"
    } finally { $archive.Dispose() }
}

function Download-Verified([string]$Url, [string]$Path, [string]$Algorithm, [string]$Expected) {
    Write-Host "Downloading $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing
    Assert-Hash $Path $Algorithm $Expected
    Assert-Zip $Path
}

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$WorkDir = (Resolve-Path $WorkDir).Path
$transcript = Join-Path $WorkDir 'gate0-transcript.txt'
Start-Transcript -LiteralPath $transcript -Force | Out-Null
try {
    Write-Host "PowerShell: $($PSVersionTable.PSVersion)  OS: $([Environment]::OSVersion.VersionString)"

    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { throw 'No java on PATH. Install a Java 17 runtime (see docs/gate0_windows.md) and open a new PowerShell window.' }
    # java -version writes to stderr. Under 'Stop', Windows PowerShell 5.1 turns redirected native stderr
    # into a terminating error, so read it with errors relaxed.
    $ErrorActionPreference = 'Continue'
    $version = (& $java.Source -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $ErrorActionPreference = 'Stop'
    Write-Host "Java on PATH: $($java.Source)"
    Write-Host $version
    if ($version -notmatch 'version "17\.') { throw 'The java on PATH is not Java 17. Forge 1.20.1 needs Java 17 for this check.' }

    $server = Join-Path $WorkDir 'server'
    $modsDir = Join-Path $server 'mods'
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    $installer = Join-Path $WorkDir "forge-$forgeVersion-installer.jar"
    Download-Verified $forgeUrl $installer 'SHA256' $forgeSha256

    foreach ($mod in $mods) {
        Download-Verified $mod.Url (Join-Path $modsDir $mod.Name) 'SHA512' $mod.Sha512
    }

    $installLog = Join-Path $WorkDir 'forge-install.log'
    $installErr = Join-Path $WorkDir 'forge-install-error.log'
    Write-Host "Running the Forge installer into $server (takes a few minutes; output goes to $installLog)"
    # Explicit target folder and working directory: without them PowerShell 7 starts java in its own folder.
    $installProc = Start-Process -FilePath $java.Source `
        -ArgumentList @('-jar', "`"$installer`"", '--installServer', "`"$server`"") `
        -WorkingDirectory $server -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $installLog -RedirectStandardError $installErr
    Write-Host "Forge installer exit code: $($installProc.ExitCode)"
    if ($installProc.ExitCode -ne 0) { throw "Forge installer failed. Send $installLog and $installErr" }

    $generated = Join-Path $server "libraries\net\minecraftforge\forge\$forgeVersion\forge-$forgeVersion-server.jar"
    Assert-Zip $generated
    $run = Join-Path $server 'run.bat'
    if (-not (Test-Path -LiteralPath $run)) { throw "Installer did not create $run" }

    Write-Host ''
    Write-Host 'GATE0 SCRIPT COMPLETE. Server files are ready. The EULA has NOT been accepted and the server has NOT been started.'
    Write-Host "Server folder: $server"
    Write-Host "Mod jars: $modsDir"
    Write-Host "Full output saved to: $transcript"
} catch {
    Write-Host ''
    Write-Host "GATE0 SCRIPT FAILED: $($_.Exception.Message)"
    Write-Host "Full output saved to: $transcript"
    throw
} finally {
    Stop-Transcript | Out-Null
}

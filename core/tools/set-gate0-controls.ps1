param([Parameter(Mandatory = $true)][string]$OptionsPath)
$ErrorActionPreference = 'Stop'
$optionsFile = (Resolve-Path -LiteralPath $OptionsPath).Path
$instanceFolder = Split-Path -Parent $optionsFile
$runningClient = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($instanceFolder,[StringComparison]::OrdinalIgnoreCase) -ge 0 }
if ($runningClient) { throw 'Close this Minecraft client before changing its saved keybindings.' }
$before = [IO.File]::ReadAllText($optionsFile)
$after = $before
foreach ($entry in @{'key_key.holdfast_core.action'='key.keyboard.f'; 'key_key.tacz.melee.desc'='key.keyboard.f'}.GetEnumerator()) {
    $pattern = '(?m)^' + [regex]::Escape($entry.Key) + ':[^\r\n]*'
    if ([regex]::IsMatch($after,$pattern)) { $after = [regex]::Replace($after,$pattern,$entry.Key+':'+$entry.Value) }
    else { $after += [Environment]::NewLine + $entry.Key + ':' + $entry.Value + [Environment]::NewLine }
}
# F normally swaps offhand items, which would change the held weapon on the same execution request.
# Move only that exact conflicting default; leave an existing custom binding alone.
$after = [regex]::Replace($after,'(?m)^key_key\.swapOffhand:key\.keyboard\.f(?=\r?$)','key_key.swapOffhand:key.keyboard.y')
if ($after -ne $before) {
    $backupFile = $optionsFile + '.holdfast-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.bak'
    Copy-Item -LiteralPath $optionsFile -Destination $backupFile
    [IO.File]::WriteAllText($optionsFile,$after,[Text.UTF8Encoding]::new($false))
    Write-Output "Saved bindings. Backup: $backupFile"
}
Write-Output 'Holdfast context: F; TaCZ melee/shove: F. Both remain editable in Controls.'

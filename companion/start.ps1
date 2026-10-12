$ErrorActionPreference = 'Stop'
$companionRoot = $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $companionRoot '.venv/Scripts/python.exe'))) {
    py -3 -m venv (Join-Path $companionRoot '.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11+ from python.org, then retry.' }
}
$companionPython = Join-Path $companionRoot '.venv/Scripts/python.exe'
& $companionPython -m pip install -r (Join-Path $companionRoot 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Dependency setup failed.' }
& $companionPython (Join-Path $companionRoot 'companion.py')

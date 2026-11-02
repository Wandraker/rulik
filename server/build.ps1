# Сборка rulik-server.exe. Запуск из папки server:  powershell -File build.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# vgamepad при установке сам запускает установщик драйвера, при сборке он не нужен
$env:VGAMEPAD_SKIP_VIGEMBUS_INSTALL = "true"
python -m pip install --disable-pip-version-check -q -r requirements.txt pyinstaller==6.16.0

python -m PyInstaller --noconfirm --clean --onefile --windowed `
    --name rulik-server `
    --icon rulik.ico `
    --add-data "rulik.ico;." `
    --collect-all vgamepad `
    run.py

Write-Host "dist\rulik-server.exe"

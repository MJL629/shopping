@echo off
rem AI-assisted local stop shortcut with Codex.
setlocal
chcp 65001 >nul
cd /d "%~dp0"
python scripts\local.py stop
if errorlevel 1 (
  echo.
  echo 停止未完成，请保留提示信息以便检查。
  pause
  exit /b 1
)
echo 本项目网页服务已停止，数据库数据仍保留。
endlocal

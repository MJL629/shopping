@echo off
rem AI-assisted local launch shortcut with Codex.
setlocal
chcp 65001 >nul
cd /d "%~dp0"
python scripts\local.py start
if errorlevel 1 (
  echo.
  echo 启动未完成。首次在新电脑运行，请先按 README 配置环境并构建项目。
  pause
  exit /b 1
)
start "" "http://127.0.0.1:18080/"
endlocal

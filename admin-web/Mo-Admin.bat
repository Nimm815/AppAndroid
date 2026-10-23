@echo off
setlocal
cd /d "%~dp0"

if not exist "node_modules\vite\bin\vite.js" goto missing_dependencies

curl.exe -fsS http://127.0.0.1:5173/ >nul 2>&1
if not errorlevel 1 goto open_admin

echo Dang khoi dong giao dien TaskMind Admin...
start "TaskMind Admin - de cua so nay mo" /D "%~dp0" cmd /k "npm.cmd run dev -- --host 127.0.0.1"
call :wait_for_vite
if errorlevel 1 goto vite_failed

:open_admin
start "" "http://127.0.0.1:5173/"
curl.exe -fsS http://127.0.0.1:8082/api/admin/stats >nul 2>&1
if errorlevel 1 echo Giao dien da mo, nhung chua ket noi duoc backend tai cong 8082.
exit /b 0

:wait_for_vite
set "attempts=0"
:wait_loop
timeout /t 1 /nobreak >nul
curl.exe -fsS http://127.0.0.1:5173/ >nul 2>&1
if not errorlevel 1 exit /b 0
set /a attempts+=1
if %attempts% geq 20 exit /b 1
goto wait_loop

:missing_dependencies
echo Thieu thu vien. Mo Terminal trong thu muc admin-web va chay: npm.cmd install
pause
exit /b 1

:vite_failed
echo Vite chua khoi dong duoc. Kiem tra cua so TaskMind Admin.
pause
exit /b 1

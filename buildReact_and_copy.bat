@echo off
cd /d %~dp0

echo === Reactアプリのビルドを開始 ===
cd frontend
call npm run build
if errorlevel 1 (
    echo ビルドに失敗しました。
    pause
    exit /b
)

echo === Spring Bootの static 配下を削除中 ===
cd ..
rmdir /s /q src\main\resources\static
mkdir src\main\resources\static

echo === buildフォルダから static にコピー中 ===
xcopy /E /I /Y frontend\build\* src\main\resources\static\

echo === 完了しました！ ===
pause

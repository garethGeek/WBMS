@echo off
setlocal

set CODEX_NODE=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe

if exist "%CODEX_NODE%" (
  "%CODEX_NODE%" node_modules\@angular\cli\bin\ng %*
) else (
  node node_modules\@angular\cli\bin\ng %*
)

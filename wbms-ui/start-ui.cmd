@echo off
setlocal

set CODEX_NODE=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe

call scripts\run-ng.cmd serve --proxy-config proxy.conf.json

Set shell = CreateObject("WScript.Shell")
appDir = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\desktop"
appUrl = "http://192.168.0.175:65502/"

shell.Run "cmd.exe /c cd /d """ & appDir & """ && npm.cmd run dev", 0, False
WScript.Sleep 2500
shell.Run appUrl, 1, False

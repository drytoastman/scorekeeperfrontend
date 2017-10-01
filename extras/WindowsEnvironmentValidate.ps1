if (!([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")) {
    Start-Process powershell.exe "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs;
    exit
}

## Check for Java 8 or above
try {
    $javaver = Get-ItemProperty -path "HKLM:\SOFTWARE\JavaSoft\Java Runtime Environment" -Name "CurrentVersion" -ErrorAction Stop
    $javamajor = [int]($javaver.CurrentVersion.Split("{.}")[1])
} catch {
    $javamajor = 0
}

if ($javamajor -lt 8) {
    Write-Host "1. Java $javamajor does not meet requirements.  Opening a browser to https://java.com/download" -foregroundcolor "yellow"
    Start-Process -FilePath "https://java.com/download"
} else {
    Write-Host "1. Java $javamajor meets requirements." -foregroundcolor "green"
}

## Check for any Docker at this point
try {
    $dockerver = docker.exe -v 
    Write-Host "2. $dockerver" -foregroundcolor "green"
} catch {
    Write-Host "2. Docker not found.                   Opening a browser to https://docs.docker.com/docker-for-windows/install" -foregroundcolor "yellow"
    Start-Process -FilePath "https://docs.docker.com/docker-for-windows/install"
}


## Make sure the firewall rules are present
$rules = Get-NetFirewallRule | Where { $_.Enabled -eq 'True' -and $_.DisplayName.StartsWith('Scorekeeper') }
$names = $rules | % { $_.DisplayName }

if (-Not ($names -contains "Scorekeeper Web")) {
    netsh advfirewall firewall add rule name="Scorekeeper Web"  dir=in action=allow protocol=TCP localport=80
}
Write-Host "3. WebServer Firewall Rule Present" -foregroundcolor "green"

if (-Not ($names -contains "Scorekeeper Database")) {
    netsh advfirewall firewall add rule name="Scorekeeper Database"   dir=in action=allow protocol=TCP localport=54329
}
Write-Host "4. Database Firewall Rule Present" -foregroundcolor "green"

if (-Not ($names -contains "Scorekeeper Timers")) {
    netsh advfirewall firewall add rule name="Scorekeeper Timers"   dir=in action=allow protocol=TCP localport=54328
}
Write-Host "5. Timers Firewall Rule Present" -foregroundcolor "green"

if (-Not ($names -contains "Scorekeeper Discovery")) {
    netsh advfirewall firewall add rule name="Scorekeeper Discovery" dir=in action=allow protocol=UDP localport=5454
}
Write-Host "6. Discovery Firewall Rule Present" -foregroundcolor "green"

Read-Host "Press Any Key To Exit"


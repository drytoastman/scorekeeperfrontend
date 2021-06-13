[Setup]
AppName=Scorekeeper
AppVersion={#Version}
OutputDir=.
AppPublisher=Brett Wilson
UsePreviousAppDir=yes
OutputBaseFilename=ScorekeeperSetup-{#Version}
DefaultDirName={autopf}\Scorekeeper
DefaultGroupName=Scorekeeper
AllowNoIcons=yes
Compression=lzma
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64
UsedUserAreasWarning=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl";

[Files]
Source: "build\image\ScorekeeperFrontend-win\*"; DestDir: "{app}"; Flags: recursesubdirs;
Source: "src\main\resources\images\cone.ico"; DestDir: "{app}";
Source: "src\main\resources\images\draglight.ico"; DestDir: "{app}";

[Icons]
Name:       "{group}\Scorekeeper"; WorkingDir: "{app}\bin"; Filename: "{app}\bin\javaw.exe"; Parameters: "-classpath ""../lib/*"" org.wwscc.system.ScorekeeperSystem"; IconFilename: "{app}\cone.ico";
Name: "{userdesktop}\Scorekeeper"; Filename: "{group}\Scorekeeper";
Name:          "{group}\ProTimer"; WorkingDir: "{app}\bin"; Filename: "{app}\bin\javaw.exe"; Parameters: "-classpath ""../lib/*"" org.wwscc.protimer.ProSoloInterface"; IconFilename: "{app}\draglight.ico";
Name:    "{userdesktop}\ProTimer"; Filename: "{group}\ProTimer";

[Run]
Filename: "{sys}\sc.exe"; Parameters: "stop   w3svc";
Filename: "{sys}\sc.exe"; Parameters: "config w3svc start=disabled";
Filename: "{sys}\sc.exe"; Parameters: "stop   SharedAccess";
Filename: "{sys}\sc.exe"; Parameters: "config SharedAccess start=disabled";
Filename: "{group}\Scorekeeper"; Description: Start App To Update Backend While Online; Flags: postinstall shellexec skipifsilent

[Code]
const
  NET_FW_PROTOCOL_TCP   = 6;
  NET_FW_PROTOCOL_UDP   = 17;
  NET_FW_PROFILE2_ALL   = 2147483647;
  NET_FW_RULE_DIR_IN    = 1;
  NET_FW_ACTION_ALLOW   = 1;

function InitializeSetup(): Boolean;
var
 ResultCode: Integer;
begin
  Result := False;

  ExecAsOriginalUser('docker.exe', 'info', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
  begin
  if ResultCode = 0 then
    Result := True
  else if ResultCode = 1 then
    MsgBox('Docker for Windows appears to be installed but not running, make sure its running and that its set to launch when you login', mbError, MB_OK)
  else
    if MsgBox('Docker for Windows is required but doesn''t appear to be installed. Do you wish to open the downlad pages now?', mbError, MB_YESNO) = IDYES then begin
        ShellExec('open', 'https://www.docker.com/products/docker-desktop', '', '', SW_SHOW, ewNoWait, ResultCode)
    end
  end;
end;


procedure AddFirewallPort(AppName: string; Protocol, Port: integer);
var
  fwPolicy: Variant;
  rule: Variant;
begin
  try
    rule            := CreateOleObject('HNetCfg.FWRule')
    rule.Name       := AppName
    rule.Profiles   := NET_FW_PROFILE2_ALL
    rule.Action     := NET_FW_ACTION_ALLOW
    rule.Direction  := NET_FW_RULE_DIR_IN
    rule.Enabled    := true
    rule.Protocol   := Protocol
    rule.LocalPorts := Port
    fwPolicy        := CreateOleObject('HNetCfg.FwPolicy2');
    fwPolicy.Rules.Add(rule);
  except
  end;
end;    

procedure RemFirewallPort(AppName: string);
var
  fwPolicy: Variant;
begin
  try
    fwPolicy := CreateOleObject('HNetCfg.FwPolicy2');
    fwPolicy.Rules.Remove(AppName);
  except
  end;
end;    

procedure AddAllRules();
begin
    AddFirewallPort('Scorekeeper Web',       NET_FW_PROTOCOL_TCP, 80);
    AddFirewallPort('Scorekeeper Timers',    NET_FW_PROTOCOL_TCP, 54328);
    AddFirewallPort('Scorekeeper Database',  NET_FW_PROTOCOL_TCP, 54329);
    AddFirewallPort('Scorekeeper DNS',       NET_FW_PROTOCOL_UDP, 53);
    AddFirewallPort('Scorekeeper MDNS',      NET_FW_PROTOCOL_UDP, 5353);
    AddFirewallPort('Scorekeeper Discovery', NET_FW_PROTOCOL_UDP, 5454);
end;

procedure RemoveAllRules();
begin
    RemFirewallPort('Scorekeeper Web');
    RemFirewallPort('Scorekeeper Timers');
    RemFirewallPort('Scorekeeper Database');
    RemFirewallPort('Scorekeeper DNS');
    RemFirewallPort('Scorekeeper MDNS');
    RemFirewallPort('Scorekeeper Discovery');
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep=ssDone then begin
    RemoveAllRules();
    AddAllRules();
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep=usPostUninstall then begin
    RemoveAllRules();
  end;
end;

end.

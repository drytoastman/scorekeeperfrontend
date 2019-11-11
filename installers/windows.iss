[Setup]
AppName=Scorekeeper
AppVersion={#Version}
OutputDir=..\build\final
AppPublisher=Brett Wilson
UsePreviousAppDir=yes
OutputBaseFilename=ScorekeeperSetup-{#Version}
DefaultDirName={autopf}\Scorekeeper\{#Version}
DefaultGroupName=Scorekeeper
AllowNoIcons=yes
Compression=lzma
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl";

[Files]
Source: "..\build\image\ScorekeeperFrontend-win-{#Version}\*"; DestDir: "{app}"; Flags: recursesubdirs
Source: "cone.ico";  DestDir: "{app}"
Source: "conep.ico"; DestDir: "{app}"

[Icons]
Name:       "{group}\Scorekeeper Prepare ({#Version})"; IconFilename: "{app}\conep.ico"; Filename: "{app}\bin\javaw.exe"; Parameters: "-classpath ""{app}\lib\*"" org.wwscc.system.ScorekeeperSystem dockerprepare"
Name:       "{group}\Scorekeeper ({#Version})";         IconFilename: "{app}\cone.ico";  Filename: "{app}\bin\javaw.exe"; Parameters: "-classpath ""{app}\lib\*"" org.wwscc.system.ScorekeeperSystem"
Name: "{userdesktop}\Scorekeeper ({#Version})";         IconFilename: "{app}\cone.ico";  Filename: "{app}\bin\javaw.exe"; Parameters: "-classpath ""{app}\lib\*"" org.wwscc.system.ScorekeeperSystem"

[Run]
Filename: "{sys}\sc.exe"; Parameters: "stop   w3svc";
Filename: "{sys}\sc.exe"; Parameters: "config w3svc start=disabled";
Filename: "{app}\bin\javaw.exe"; Parameters: "-classpath ""{app}\lib\*"" org.wwscc.system.ScorekeeperSystem dockerprepare";

[Code]
const
  NET_FW_PROTOCOL_TCP   = 6;
  NET_FW_PROTOCOL_UDP   = 17;
  NET_FW_PROFILE2_ALL   = 2147483647;
  NET_FW_RULE_DIR_IN    = 1;
  NET_FW_ACTION_ALLOW   = 1;

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
    AddFirewallPort('Scorekeeper Discovery', NET_FW_PROTOCOL_UDP, 5454);
end;

procedure RemoveAllRules();
begin
    RemFirewallPort('Scorekeeper Web');
    RemFirewallPort('Scorekeeper Timers');
    RemFirewallPort('Scorekeeper Database');
    RemFirewallPort('Scorekeeper Discovery');
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep=ssPostInstall then begin
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

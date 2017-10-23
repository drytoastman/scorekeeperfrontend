[Setup]
AppName=Scorekeeper
AppVersion={#Version}
OutputDir=.
AppPublisher=Brett Wilson
UsePreviousAppDir=yes
OutputBaseFilename=ScorekeeperSetup-{#Version}
DefaultDirName={pf}\Scorekeeper
DefaultGroupName=Scorekeeper
AllowNoIcons=yes
Compression=lzma
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl";

[Files]
Source: "..\build\libs\scorekeeper-{#Version}.jar"; DestDir: "{app}";

[Icons]
Name: "{group}\TrayMonitor"; WorkingDir: "{app}"; Filename: "javaw.exe"; Parameters: "-jar scorekeeper-{#Version}.jar";
Name: "{group}\Uninstall";   WorkingDir: "{app}"; Filename: "{app}\unins000.exe";

[Run]
Filename: "{sys}\sc.exe"; Parameters: "stop   w3svc";
Filename: "{sys}\sc.exe"; Parameters: "config w3svc start=disabled";
Filename: "docker-machine.exe"; Parameters: "create default"; Flags: runascurrentuser waituntilterminated; StatusMsg: "Creating Docker VM";
Filename: {code:ImageBatchFile};                              Flags: runascurrentuser waituntilterminated; StatusMsg: "Download database images";

[Code]
const
  NET_FW_PROTOCOL_TCP   = 6;
  NET_FW_PROTOCOL_UDP   = 17;
  NET_FW_PROFILE2_ALL   = 2147483647;
  NET_FW_RULE_DIR_IN    = 1;
  NET_FW_ACTION_ALLOW   = 1;

function InitializeSetup(): Boolean;
var
 Version: String;
 ResultCode: Integer;
 JavaOk: Boolean;
 DockerOk: Boolean;
 Msg: String;
begin
   JavaOk := false;
   DockerOk := False;
   Result := True;

   if RegQueryStringValue(HKLM, 'SOFTWARE\JavaSoft\Java Runtime Environment', 'CurrentVersion', Version) then begin
     if (StrToInt(Version[3]) >= 8) then begin
        JavaOk := True;
     end;
   end;

   if ExecAsOriginalUser('docker-machine.exe', '--version', '', SW_SHOW, ewWaitUntilTerminated, ResultCode) then begin
     DockerOk := True;
   end;
   
   if not JavaOk or not DockerOk then begin
      Result := False;
      Msg := 'The necessary installl requirements are not met.'#13#10#13#10;
      if not JavaOk then begin
        Msg := Msg + ' - Java version 1.8 or newer is required.'#13#10;
      end;
      if not DockerOk then begin
        Msg := Msg + ' - Docker-Toolbox for Windows is required.'#13#10;
      end;
      
      Msg := Msg + #13#10'Once the indicated software is installed, you can rerun this script.  Do you wish to open the downlad pages now?';
      
      if MsgBox(Msg, mbConfirmation, MB_YESNO) = IDYES then begin
        if not JavaOk then begin
          ShellExec('open', 'http://java.com/en/download/windows-64bit.jsp', '', '', SW_SHOW, ewNoWait, ResultCode);
        end;
        if not DockerOk then begin
          ShellExec('open', 'https://docs.docker.com/toolbox/toolbox_install_windows/', '', '', SW_SHOW, ewNoWait, ResultCode);
        end;
      end;
   end;
end;


function ImageBatchFile(ignored: String): String;
var
 ResultCode: Integer;
begin
   Result := ExpandConstant('{tmp}\pullimages.bat')
   if ExecAsOriginalUser(ExpandConstant('{cmd}'), ExpandConstant('/C docker-machine.exe env --shell cmd > '+Result), '', SW_SHOW, ewWaitUntilTerminated, ResultCode) then begin
       SaveStringToFile(Result, 'docker pull drytoastman/scdb:{#Version}'#10, True);
       SaveStringToFile(Result, 'docker pull drytoastman/scweb:{#Version}'#10, True);
       SaveStringToFile(Result, 'docker pull drytoastman/scsync:{#Version}'#10, True);
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

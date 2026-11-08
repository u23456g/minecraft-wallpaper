$ErrorActionPreference = 'Stop'
Add-Type @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class SilentWindowProbe {
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 delegate bool EnumWindowsCallback(IntPtr window, IntPtr arg);
 [DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsCallback callback, IntPtr arg);
 [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
 [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder text, int count);
 public static string ForegroundOwner() {
  uint pid; GetWindowThreadProcessId(GetForegroundWindow(),out pid);
  try { return System.Diagnostics.Process.GetProcessById((int)pid).ProcessName+":"+pid; }
  catch(ArgumentException) { return "none"; }
 }
 public static string[] VisibleSteam() {
  var result = new List<string>();
  EnumWindows((h,a)=> {
   if (!IsWindowVisible(h)) return true;
   uint pid; GetWindowThreadProcessId(h,out pid);
   try {
    var name=System.Diagnostics.Process.GetProcessById((int)pid).ProcessName;
    if(name=="steam" || name=="steamwebhelper") {
     var title=new StringBuilder(1024);GetWindowText(h,title,1024);
     result.Add(h.ToInt64()+":"+title.ToString());
    }
   } catch(ArgumentException) { }
   return true;
  },IntPtr.Zero);
  return result.ToArray();
 }
}
'@
$gson = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
if (!$gson) { throw 'Gson not found' }
$before = @([SilentWindowProbe]::VisibleSteam())
$foreground = [SilentWindowProbe]::GetForegroundWindow().ToInt64()
$newWindows = [System.Collections.Generic.HashSet[string]]::new()
$focusChanged = $false
$cp = "run/acquisition-smoke;build/classes/java/main;$gson"
$child = Start-Process java -ArgumentList @('-cp', ('"' + $cp + '"'), 'WorkshopAcquisitionSmoke', 'E:\Steam', '3444535389') -NoNewWindow -RedirectStandardOutput run/silent-probe.out -RedirectStandardError run/silent-probe.err -PassThru
$processHandle = $child.Handle
$foregroundOwners = [System.Collections.Generic.HashSet[string]]::new()
$deadline = [datetime]::UtcNow.AddSeconds(45)
do {
 foreach ($window in [SilentWindowProbe]::VisibleSteam()) {
  if ($window -notin $before) { [void]$newWindows.Add($window) }
 }
 if ([SilentWindowProbe]::GetForegroundWindow().ToInt64() -ne $foreground) {
  $focusChanged = $true
  [void]$foregroundOwners.Add([SilentWindowProbe]::ForegroundOwner())
 }
 Start-Sleep -Milliseconds 100
 $child.Refresh()
} while (!$child.HasExited -and [datetime]::UtcNow -lt $deadline)
if (!$child.HasExited) { throw 'Download still running after 45s; no successful verdict' }
$child.WaitForExit()
Write-Output "Steam windows before: $($before -join '; ')"
Write-Output "New visible Steam windows sampled: $($newWindows -join '; ')"
Write-Output "Foreground changed during observation: $focusChanged; owners: $($foregroundOwners -join '; ')"
Write-Output "Download exit code: $($child.ExitCode)"
if ($child.ExitCode -ne 0) { throw 'Live download failed; inspect run/silent-probe.err' }
if ($newWindows.Count -ne 0 -or $focusChanged) { throw 'Window/focus observation did not meet silent-download acceptance' }
Write-Output 'PASS: warm Steam download completed without new visible Steam windows or foreground changes (100ms samples)'

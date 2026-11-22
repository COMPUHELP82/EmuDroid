# EmuDroid - pone la ventana del emulador en pantalla completa (sin bordes) o la devuelve a ventana normal.
# Uso: ventana.ps1 -Name Medium_Phone -Mode full|window [-Emulator ruta\emulator.exe -EmuArgs "-avd X -skin WxH"]
param(
    [string]$Name,
    [string]$Mode = 'full',
    [string]$Emulator = '',
    [string]$EmuArgs = ''
)

Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Runtime.InteropServices;

public class EmuWin {
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr h, int i);
    [DllImport("user32.dll")] public static extern int SetWindowLong(IntPtr h, int i, int v);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int i);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();

    const int GWL_STYLE = -16;
    const int WS_CAPTION = 0x00C00000;
    const int WS_THICKFRAME = 0x00040000;
    const uint SWP_FRAMECHANGED = 0x0020;
    const uint SWP_SHOWWINDOW = 0x0040;

    public static IntPtr Find(string prefix) {
        IntPtr found = IntPtr.Zero;
        EnumWindows(delegate (IntPtr h, IntPtr l) {
            if (!IsWindowVisible(h)) return true;
            StringBuilder sb = new StringBuilder(512);
            GetWindowText(h, sb, 512);
            if (sb.ToString().StartsWith(prefix, StringComparison.Ordinal)) { found = h; return false; }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static void Full(IntPtr h) {
        int st = GetWindowLong(h, GWL_STYLE);
        SetWindowLong(h, GWL_STYLE, st & ~(WS_CAPTION | WS_THICKFRAME));
        int w = GetSystemMetrics(0), hh = GetSystemMetrics(1);
        SetWindowPos(h, IntPtr.Zero, 0, 0, w, hh, SWP_FRAMECHANGED | SWP_SHOWWINDOW);
        SetForegroundWindow(h);
    }

    public static void Windowed(IntPtr h) {
        int st = GetWindowLong(h, GWL_STYLE);
        SetWindowLong(h, GWL_STYLE, st | WS_CAPTION | WS_THICKFRAME);
        int w = GetSystemMetrics(0), hh = GetSystemMetrics(1);
        int cw = w * 3 / 4, ch = hh * 3 / 4;
        SetWindowPos(h, IntPtr.Zero, (w - cw) / 2, (hh - ch) / 2, cw, ch, SWP_FRAMECHANGED | SWP_SHOWWINDOW);
        SetForegroundWindow(h);
    }
}
"@

[EmuWin]::SetProcessDPIAware() | Out-Null

if ($Emulator -ne '') {
    Start-Process -FilePath $Emulator -ArgumentList $EmuArgs -WorkingDirectory (Split-Path $Emulator)
}

$prefix = "Android Emulator - " + $Name + ":"
$h = [IntPtr]::Zero
for ($i = 0; $i -lt 360 -and $h -eq [IntPtr]::Zero; $i++) {
    $h = [EmuWin]::Find($prefix)
    if ($h -eq [IntPtr]::Zero) { Start-Sleep -Milliseconds 500 }
}
if ($h -eq [IntPtr]::Zero) { Write-Output 'NOTFOUND'; exit 1 }

# Esperar a que la ventana termine de acomodarse
Start-Sleep -Milliseconds 2500

if ($Mode -eq 'full') { [EmuWin]::Full($h) } else { [EmuWin]::Windowed($h) }
Write-Output 'OK'

# 查询 Build Manager 工作流最近一次运行的状态
param([int]$Jobs = 100)

Add-Type -Namespace CS -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
if (-not [CS.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p)) { "no credential"; exit 1 }
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][CS.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[CS.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps"; Accept = "application/vnd.github+json" }

$r = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs?per_page=3" -Headers $h -Proxy $null -TimeoutSec 25
$run = $r.workflow_runs | Where-Object { $_.name -eq "Build Manager" } | Select-Object -First 1
"run=$($run.status)/$($run.conclusion) sha=$($run.head_sha.Substring(0,7)) id=$($run.id)"
$j = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$($run.id)/jobs?per_page=$Jobs" -Headers $h -Proxy $null -TimeoutSec 25
$j.jobs | ForEach-Object { "$($_.status) | $($_.conclusion) | $($_.name)" }

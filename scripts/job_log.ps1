param([string]$RunId, [string]$JobMatch, [int]$Tail = 60)

Add-Type -Namespace JL -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
[JL.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p) | Out-Null
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][JL.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[JL.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps"; Accept = "application/vnd.github+json" }

$j = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$RunId/jobs?per_page=100" -Headers $h -Proxy $null -TimeoutSec 30
$job = $j.jobs | Where-Object { $_.name -like "*$JobMatch*" } | Select-Object -First 1
if (-not $job) { "job not found"; exit 1 }
"job=$($job.name) conclusion=$($job.conclusion)"

$log = Invoke-WebRequest -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/jobs/$($job.id)/logs" -Headers $h -Proxy $null -MaximumRedirection 5 -UseBasicParsing -TimeoutSec 60
$lines = $log.Content -split "`n"
$lines | Select-Object -Last $Tail | ForEach-Object { $_.TrimEnd() }

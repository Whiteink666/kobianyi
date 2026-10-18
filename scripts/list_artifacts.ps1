param([string]$RunId, [string]$Filter = "")

Add-Type -Namespace LA -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
[LA.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p) | Out-Null
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][LA.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[LA.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps" }

$a = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$RunId/artifacts?per_page=100" -Headers $h -Proxy $null -TimeoutSec 30
$a.artifacts | Where-Object { -not $Filter -or $_.name -like "*$Filter*" } | ForEach-Object { "$($_.name)`t$($_.size_in_bytes)`texpired=$($_.expired)" }

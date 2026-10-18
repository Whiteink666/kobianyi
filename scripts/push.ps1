# 用本机保存的 GitHub 凭据推送当前分支到指定远端
param([string]$Remote = "kobianyi", [string]$Branch = "main")

Add-Type -Namespace PS -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
if (-not [PS.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p)) { "no credential"; exit 1 }
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][PS.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[PS.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$b64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("Whiteink666:$tok"))

# 本机代理 (127.0.0.1) 常常没在跑，先直连，不行再走代理
foreach ($mode in @("direct", "direct", "proxy", "direct", "proxy")) {
    if ($mode -eq "direct") {
        $a = @("-c", "http.proxy=", "-c", "https.proxy=")
    } else {
        $a = @("-c", "http.proxy=http://127.0.0.1:7890", "-c", "https.proxy=http://127.0.0.1:7890")
    }
    "--- attempt $mode ---"
    git @a -c "http.https://github.com/.extraHeader=Authorization: Basic $b64" push $Remote $Branch 2>&1 | Select-Object -Last 4
    if ($LASTEXITCODE -eq 0) { "PUSH OK"; break }
}

$tok = $null
$b64 = $null

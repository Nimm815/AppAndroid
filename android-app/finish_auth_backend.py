from pathlib import Path
r=Path('../backend-spring')
for service in ['task','productivity']:
 p=r/f'{service}-service/src/main/java/vn/edu/taskmind/{service}/api/NativeIdentity.java'
 s=p.read_text(encoding='utf-8').replace(f'HttpStatus.{service}_UNAVAILABLE','HttpStatus.SERVICE_UNAVAILABLE');p.write_text(s,encoding='utf-8')
 p=r/f'{service}-service/src/main/java/vn/edu/taskmind/{service}/api/CurrentUser.java'
 s=p.read_text(encoding='utf-8').replace('public CurrentUser(Environment environment)', 'private final NativeIdentity identity;\n    public CurrentUser(Environment environment, NativeIdentity identity)').replace('        if (environment.matchesProfiles','        this.identity=identity;\n        if (environment.matchesProfiles').replace('return 1;', 'return identity.id();').replace('Development identity only. Replace this boundary with verified Auth identity later.','All data APIs require a verified Auth session.');p.write_text(s,encoding='utf-8')
 p=r/f'{service}-service/src/test/java/vn/edu/taskmind/{service}/ApiIntegrationTest.java'
 s=p.read_text(encoding='utf-8').replace('class ApiIntegrationTest {',f'''class ApiIntegrationTest {{
 @org.springframework.test.context.bean.override.mockito.MockitoBean vn.edu.taskmind.{service}.api.NativeIdentity identity;
 @org.junit.jupiter.api.BeforeEach void identity(){{org.mockito.Mockito.when(identity.id()).thenReturn(1);}}
''');p.write_text(s,encoding='utf-8')
p=r/'productivity-service/src/test/java/vn/edu/taskmind/productivity/HabitStatisticsTest.java';s=p.read_text(encoding='utf-8').replace('CurrentUser(environment)','CurrentUser(environment,null)');p.write_text(s,encoding='utf-8')
# Update HTTP integration harness to exercise real authentication across service processes.
p=r/'tools/Test-ApiPersistence.ps1';s=p.read_text(encoding='utf-8');s=s.replace('--spring.jpa.hibernate.ddl-auto=validate"','--spring.jpa.hibernate.ddl-auto=validate --taskmind.auth-url=http://127.0.0.1:18081"')
s=s.replace('    if($null -ne $body){','    if($script:sessionToken){$options.Headers=@{Authorization="Bearer $script:sessionToken"}}\n    if($null -ne $body){')
s=s.replace("    $taskProcess=Start-TestApi 'task' 18082",'''    $null=Start-TestApi 'auth' 18081
    $account=Http POST 18081 '/api/auth/register' @{email=([guid]::NewGuid().ToString('N')+'@test.invalid');password=([guid]::NewGuid().ToString('N'));fullName='HTTP test'} 201
    $script:sessionToken=$account.token
    $taskProcess=Start-TestApi 'task' 18082''',1)
s=s.replace('} finally {\n    foreach($process','} finally {\n    $script:sessionToken=$null\n    foreach($process')
anchor="    $a=Http POST 18082 '/api/lists'"
idx=s.index(anchor)
s=s[:idx]+'''    $device=[guid]::NewGuid().ToString()
    $native=Http PUT 18082 "/api/native-workspace/$device" @{revision=1;tasks=@(@{id='local1';title='Native authenticated';scheduledDate='2026-10-23';priority=2;progress=35;note=''});lists=@()}
    $null=Http PUT 18083 "/api/native-workspace/$device" @{revision=1;habits=@(@{id='habit1';title='Native habit';body='';created='2026-10-23';progress=@{'2026-10-23'=25}})}
    $firstToken=$script:sessionToken
    $other=Http POST 18081 '/api/auth/register' @{email=([guid]::NewGuid().ToString('N')+'@test.invalid');password=([guid]::NewGuid().ToString('N'));fullName='Other account'} 201
    $script:sessionToken=$other.token
    foreach($port in @(18082,18083)){
        $code=0
        try { $null=Invoke-WebRequest "http://127.0.0.1:$port/api/native-workspace/$device" -UseBasicParsing -Headers @{Authorization="Bearer $script:sessionToken"} }
        catch {if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode}else{throw}}
        if($code -ne 404){throw 'Another account could access the native workspace.'}
        $code=0
        try {$null=Invoke-WebRequest "http://127.0.0.1:$port/api/native-workspace/$device" -UseBasicParsing}
        catch {if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode}else{throw}}
        if($code -ne 401){throw 'Native workspace accessible without login.'}
    }
    $script:sessionToken=$firstToken
    Write-Output 'Authenticated native sync PASS: real Auth token, both services, account isolation and anonymous rejection.'
'''+s[idx:]
p.write_text(s,encoding='utf-8')

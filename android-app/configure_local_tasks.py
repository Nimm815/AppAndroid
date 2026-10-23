from pathlib import Path
r=Path('../backend-spring')
p=r/'task-service/src/main/java/vn/edu/taskmind/task/api/NativeIdentity.java'
s=p.read_text(encoding='utf-8').replace(' private final RestClient auth;',''' private final RestClient auth;
 @Value("${taskmind.local-workspace:false}") private boolean localWorkspace;
 @Value("${server.address:}") private String bindAddress;
 @Value("${taskmind.local-user-id:1}") private int localUserId;
 @jakarta.annotation.PostConstruct void validateLocalWorkspace(){
  if(localWorkspace && (!"127.0.0.1".equals(bindAddress)||localUserId<1))
   throw new IllegalStateException("Local workspace requires server.address=127.0.0.1 and a positive owner ID");
 }''').replace('  Object cached=request.getAttribute', '''  if(localWorkspace && request.getRequestURI().startsWith("/api/native-workspace/")
      && "127.0.0.1".equals(request.getRemoteAddr())) return localUserId;
  Object cached=request.getAttribute''')
p.write_text(s,encoding='utf-8')
p=r/'tools/Update-DevelopmentBackend.ps1'
p.write_text('''param()
$ErrorActionPreference='Stop'
$backend=Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\\Set-DatabaseEnvironment.ps1" -Service task
$wrapper=Join-Path $backend '..\\android-app\\gradlew.bat'
& $wrapper -p $backend :task-service:bootJar
if($LASTEXITCODE -ne 0){throw 'Build failed. Existing services were not stopped.'}
& "$PSScriptRoot\\Stop-AllServices.ps1" -Services task
$port=if($env:TASK_PORT){[int]$env:TASK_PORT}else{8082}
if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq $port).Count){throw "Port $port occupied by unmanaged process."}
$jar=Join-Path $backend 'task-service\\build\\libs\\task-service-0.1.0.jar'
& java -jar $jar --spring.profiles.active=migrate --spring.profiles.include=default --debug=false --trace=false
if($LASTEXITCODE -ne 0){throw 'Task migration failed. No data reset attempted.'}
$oldLocal=$env:TASKMIND_LOCAL_WORKSPACE
$oldAddress=$env:SERVER_ADDRESS
try {
    $env:TASKMIND_LOCAL_WORKSPACE='true'
    $env:SERVER_ADDRESS='127.0.0.1'
    & "$PSScriptRoot\\Start-AllServices.ps1" -Services task -DevelopmentApi
} finally {
    $env:TASKMIND_LOCAL_WORKSPACE=$oldLocal
    $env:SERVER_ADDRESS=$oldAddress
}
$device=[guid]::NewGuid().ToString()
$code=0
try {$response=Invoke-WebRequest "http://127.0.0.1:$port/api/native-workspace/$device" -UseBasicParsing -TimeoutSec 10;$code=[int]$response.StatusCode}
catch {if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode}else{throw}}
if($code -ne 404){throw "Expected no existing workspace (404), received $code."}
Write-Host 'Task service ready on localhost. Native workspace does not require Android login; Auth/Productivity are not required.'
''',encoding='utf-8')
p=r/'task-service/src/test/java/vn/edu/taskmind/task/NativeWorkspaceIntegrationTest.java'
s=p.read_text(encoding='utf-8')
s=s.replace('class NativeWorkspaceIntegrationTest','class LocalWorkspaceIntegrationTest').replace('@SpringBootTest @AutoConfigureMockMvc','@SpringBootTest(properties={"taskmind.local-workspace=true","server.address=127.0.0.1"}) @AutoConfigureMockMvc')
s='\n'.join(line for line in s.splitlines() if 'MockitoBean' not in line and '@org.junit.jupiter.api.BeforeEach' not in line)
# This copied test exercises native routes only; authenticated helper methods are unused.
(p.parent/'LocalWorkspaceIntegrationTest.java').write_text(s,encoding='utf-8')

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$server = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe'
$config = 'database/mysql/my.ini'

$client = [System.Net.Sockets.TcpClient]::new()
try {
    $connect = $client.BeginConnect('127.0.0.1', 3306, $null, $null)
    if ($connect.AsyncWaitHandle.WaitOne(500) -and $client.Connected) {
        exit 0
    }
} finally {
    $client.Dispose()
}

if (-not (Test-Path -LiteralPath $server)) {
    throw "MySQL Server was not found at $server"
}

Start-Process -FilePath $server -ArgumentList "--defaults-file=$config" -WorkingDirectory $projectRoot -WindowStyle Hidden

for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 500
    $probe = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $probe.BeginConnect('127.0.0.1', 3306, $null, $null)
        if ($connect.AsyncWaitHandle.WaitOne(300) -and $probe.Connected) {
            exit 0
        }
    } finally {
        $probe.Dispose()
    }
}

throw 'MySQL did not start on port 3306.'

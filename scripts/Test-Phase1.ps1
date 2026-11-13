param([string]$BaseUrl = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAILED: $Message" }
    Write-Host "PASS: $Message"
}
$health = Invoke-RestMethod "$BaseUrl/actuator/health"
Assert-Check ($health.status -eq 'UP') 'API and MongoDB health'
$movies = Invoke-RestMethod "$BaseUrl/api/v1/movies?page=0&size=12"
Assert-Check ($movies.items.Count -gt 0) 'Catalogue returns seeded movies from MongoDB'
$movie = Invoke-RestMethod "$BaseUrl/api/v1/movies/orbit-nine"
Assert-Check ($movie.id -eq 'orbit-nine') 'Movie detail identity'
$search = Invoke-RestMethod "$BaseUrl/api/v1/movies?q=Orbit"
Assert-Check (@($search.items | Where-Object id -eq 'orbit-nine').Count -eq 1) 'Title search'
$literal = Invoke-RestMethod "$BaseUrl/api/v1/movies?q=%5B"
Assert-Check ($literal.totalElements -eq 0) 'Search treats regex characters as literal text'
$filters = Invoke-RestMethod "$BaseUrl/api/v1/catalogue/filters"
Assert-Check ($filters.cities.Count -gt 0) 'Filter facets'
$indiaNow = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'India Standard Time')
$showDate = $indiaNow.AddDays(1).ToString('yyyy-MM-dd')
$showtimes = Invoke-RestMethod "$BaseUrl/api/v1/showtimes?movieId=orbit-nine&date=$showDate"
Assert-Check ($showtimes.Count -gt 0) 'Movie showtimes'
$seatMap = Invoke-RestMethod "$BaseUrl/api/v1/showtimes/$($showtimes[0].id)/seats"
$seats = @($seatMap.rows | ForEach-Object { $_.seats })
Assert-Check ($seats.Count -eq $showtimes[0].totalSeats) 'Seat map agrees with showtime capacity'
Assert-Check (@($seats | Where-Object status -eq 'AVAILABLE').Count -eq $showtimes[0].availableSeats) 'Seat availability agrees with MongoDB inventory'
Assert-Check (@($seats | Group-Object id | Where-Object Count -gt 1).Count -eq 0) 'Seat identifiers are unique within the show'
$spec = Invoke-RestMethod "$BaseUrl/v3/api-docs"
Assert-Check ($null -ne $spec.paths.'/api/v1/movies') 'OpenAPI documents the catalogue'
function Assert-Status([string]$Path, [int]$Expected, [string]$Method = 'GET') {
    $status = 0
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl$Path" -Method $Method -UseBasicParsing
        $status = [int]$response.StatusCode
    } catch {
        if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        else { throw }
    }
    Assert-Check ($status -eq $Expected) "$Method $Path returns $Expected"
}
Assert-Status '/api/v1/movies?page=-1' 400
Assert-Status '/api/v1/movies/does-not-exist' 404
Assert-Status '/api/v1/movies' 401 'POST'
Write-Host 'Phase 1 smoke checks passed. These checks do not test reservations, payments, or concurrency.'

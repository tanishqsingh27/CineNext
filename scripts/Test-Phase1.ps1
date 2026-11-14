param([string]$BaseUrl = 'http://127.0.0.1:8080', [string]$ShowDate)
$ErrorActionPreference = 'Stop'
function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAILED: $Message" }
    Write-Host "PASS: $Message"
}
function Assert-Status([string]$Path, [int]$Expected, [string]$Method = 'GET') {
    $status = 0
    try { $status = [int](Invoke-WebRequest -Uri "$BaseUrl$Path" -Method $Method -UseBasicParsing).StatusCode }
    catch { if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode } else { throw } }
    Assert-Check ($status -eq $Expected) "$Method $Path returns $Expected"
}
$health = Invoke-RestMethod "$BaseUrl/actuator/health"
Assert-Check ($health.status -eq 'UP') 'API and MongoDB health'
$movies = Invoke-RestMethod "$BaseUrl/api/v1/movies?page=0&size=12"
Assert-Check ($movies.totalElements -ge @($movies.items).Count -and $movies.page -eq 0) 'Published catalogue supports empty results'
if (@($movies.items).Count -gt 0) {
    $firstMovie=$movies.items[0]
    $movie = Invoke-RestMethod "$BaseUrl/api/v1/movies/$($firstMovie.id)"
    Assert-Check ($movie.id -eq $firstMovie.id) 'Published movie detail identity'
    $term=[Uri]::EscapeDataString($firstMovie.title)
    $search = Invoke-RestMethod "$BaseUrl/api/v1/movies?q=$term&size=50"
    Assert-Check (@($search.items | Where-Object id -eq $firstMovie.id).Count -eq 1) 'Search finds an administrator-saved movie'
} else { Write-Host 'INFO: No published movies; detail/search checks await administrator content.' }
$literal = Invoke-RestMethod "$BaseUrl/api/v1/movies?q=%5B"
Assert-Check (@($literal.items | Where-Object { -not $_.title.Contains('[') }).Count -eq 0) 'Search treats regex characters as literal text'
$filters = Invoke-RestMethod "$BaseUrl/api/v1/catalogue/filters"
Assert-Check ($null -ne $filters.PSObject.Properties['cities'] -and $null -ne $filters.PSObject.Properties['genres'] -and $null -ne $filters.PSObject.Properties['languages']) 'Filter facets support an empty catalogue'
$theatres=Invoke-RestMethod "$BaseUrl/api/v1/theatres"
Assert-Check (@($filters.cities | Where-Object { $_ -notin $theatres.city }).Count -eq 0) 'City choices come from published cinemas'
$indiaNow = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'India Standard Time')
if(-not $ShowDate) { $ShowDate = $indiaNow.AddDays(1).ToString('yyyy-MM-dd') }
$showtimes = Invoke-RestMethod "$BaseUrl/api/v1/showtimes?date=$ShowDate"
Assert-Check (@($showtimes | Where-Object { [DateTimeOffset]$_.startsAt -le [DateTimeOffset]::UtcNow }).Count -eq 0) 'Catalogue omits elapsed showtimes'
if ($showtimes.Count -gt 0) {
    $seatMap = Invoke-RestMethod "$BaseUrl/api/v1/showtimes/$($showtimes[0].id)/seats"
    $seats = @($seatMap.rows | ForEach-Object { $_.seats })
    Assert-Check ($seats.Count -eq $showtimes[0].totalSeats) 'Seat map agrees with published showtime capacity'
    Assert-Check (@($seats | Where-Object status -eq 'AVAILABLE').Count -eq $showtimes[0].availableSeats) 'Seat availability agrees with MongoDB inventory'
    Assert-Check (@($seats | Group-Object id | Where-Object Count -gt 1).Count -eq 0) 'Seat identifiers are unique within the show'
} else { Write-Host "INFO: No published shows on $ShowDate; inventory checks await an admin show. Use -ShowDate YYYY-MM-DD to choose a date." }
$spec = Invoke-RestMethod "$BaseUrl/v3/api-docs"
Assert-Check ($null -ne $spec.paths.'/api/v1/movies') 'OpenAPI documents the catalogue'
Assert-Status '/api/v1/movies?page=-1' 400
Assert-Status '/api/v1/movies/does-not-exist' 404
Assert-Status '/api/v1/movies' 401 'POST'
Write-Host 'Catalogue smoke checks passed. No demo data was required or created; transaction/concurrency checks belong to the integration suite.'

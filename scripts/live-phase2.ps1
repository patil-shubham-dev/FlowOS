# Live Phase-2 verifications: the mechanisms scoped pages rely on, against the
# real backend. (What the UI puts in visibleIds is code-reviewed in the page
# components — these checks prove the server honors exactly that scope.)
# Run while the server is up:
#   $env:FLOWOS_DB="..."; $env:FLOWOS_PORT="3106"; node dist/server.js
#   & "C:\Users\91808\Desktop\FlowOS\scripts\live-phase2.ps1"
$h = @{ "Content-Type" = "application/json" };
$base = "http://localhost:3106";
$fail = 0;
function Check($name, $cond) {
  if ($cond) { Write-Output "PASS: $name" } else { Write-Output "FAIL: $name"; $script:fail += 1 }
}
function Turn($instruction, $steps, $approval, $session, $context = @{ surface = "home" }) {
  $body = @{ context = $context; instruction = $instruction; plan = @{ steps = $steps }; sessionId = $session };
  if ($approval) { $body.approval = $approval }
  return Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body | ConvertTo-Json -Depth 10) -Headers $h;
}
function Scoped($surface, $visibleIds) {
  return @{ surface = $surface; visibleIds = $visibleIds };
}

# ---- TASKS: scope resolves from the page's visibleIds, not a server re-query
$a = Turn "s" @(@{ tool = "createTask"; input = @{ title = "t1"; priority = "urgent" } }) $null "p2t" | Out-Null
$b = Turn "s" @(@{ tool = "createTask"; input = @{ title = "t2"; priority = "urgent" } }) $null "p2t" | Out-Null
$c = Turn "s" @(@{ tool = "createTask"; input = @{ title = "t3"; priority = "urgent" } }) $null "p2t" | Out-Null
$ids = ((Invoke-RestMethod -Uri "$base/api/tasks").tasks | Where-Object { $_.title -like "t*" } | Select-Object -ExpandProperty id)
# The pill sends ids for exactly the 2 VISIBLE urgent rows (page state):
$subset = @($ids[0], $ids[1])
$p = Turn "Mark all visible urgent tasks done" @(@{ tool = "updateTask"; input = @{ ids = $subset; patch = @{ status = "done" } } }) $null "p2t-tasks"
Check "tasks: ids-scoped plan gates confirm" ($p.status -eq "needs-approval" -and $p.ui -eq "diff-card")
Check "tasks: affected ids equal the sent visible subset" (($p.proposals[0].affectedIds -join ",") -eq ($subset -join ","))
$ap = @{ context = (Scoped "scoped:tasks" $subset); instruction = "x"; plan = @{ steps = @(@{ tool = "updateTask"; input = @{ ids = $subset; patch = @{ status = "done" } } }) }; approval = $p.approvalTemplate; sessionId = "p2t-tasks" }
$done = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($ap | ConvertTo-Json -Depth 10) -Headers $h
Check "tasks: only the visible subset changed" ($done.status -eq "executed")
$after = (Invoke-RestMethod -Uri "$base/api/tasks").tasks | Where-Object { $_.title -like "t*" }
Check "tasks: third row untouched" (($after | Where-Object { $_.id -eq $ids[2] }).status -eq "todo" -and (($after | Where-Object { $_.status -eq "done" }).Count -eq 2))

# ---- CALENDAR: reschedule cascade renders grouped with single undo
$day = "2026-10-05"
Turn "s" @(@{ tool = "createEvent"; input = @{ title = "Maths"; startAt = "${day}T17:00:00"; endAt = "${day}T18:00:00" } }) $null "p2c" | Out-Null
Turn "s" @(@{ tool = "createEvent"; input = @{ title = "Reading"; startAt = "${day}T19:00:00"; endAt = "${day}T19:30:00"; isFlexible = $true } }) $null "p2c" | Out-Null
$evs = (Invoke-RestMethod -Uri "$base/api/events?from=${day}T00:00:00&to=${day}T23:59:59").events
$maths = ($evs | Where-Object { $_.title -eq "Maths" }).id
$reading = ($evs | Where-Object { $_.title -eq "Reading" }).id
$rp = Turn "shift" @(@{ tool = "reschedule"; input = @{ eventId = $maths; newStartAt = "${day}T19:00:00"; newEndAt = "${day}T20:00:00" } }) $null "p2c-cal"
Check "calendar: cascade detected (2 affected, grouped)" ($rp.status -eq "needs-approval" -and $rp.proposals[0].affectedIds.Count -eq 2)
$ap2 = @{ context = (Scoped "scoped:calendar" @($maths, $reading)); instruction = "shift"; plan = @{ steps = @(@{ tool = "reschedule"; input = @{ eventId = $maths; newStartAt = "${day}T19:00:00"; newEndAt = "${day}T20:00:00" } }) }; approval = $rp.approvalTemplate; sessionId = "p2c-cal" }
$shifted = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($ap2 | ConvertTo-Json -Depth 10) -Headers $h
Check "calendar: executes with grouped undo" ($shifted.status -eq "executed" -and $null -ne $shifted.groupId)
$log = (Invoke-RestMethod -Uri "$base/api/log?limit=50").entries | Where-Object { $_.groupId -eq $shifted.groupId }
Check "calendar: one group, two rows" ($log.Count -eq 2)
$u = Invoke-RestMethod -Uri "$base/api/undo" -Method Post -Body (@{ entryId = $log[0].id } | ConvertTo-Json) -Headers $h
$restored = (Invoke-RestMethod -Uri "$base/api/events?from=${day}T00:00:00&to=${day}T23:59:59").events
# Compare as instants: the server normalizes to UTC ISO strings.
$mathsOk = [DateTime]($restored | Where-Object { $_.id -eq $maths }).startAt -eq [DateTime]"${day}T17:00:00"
$readingOk = [DateTime]($restored | Where-Object { $_.id -eq $reading }).startAt -eq [DateTime]"${day}T19:00:00"
Check "calendar: single undo restores both" ($u.ok -and $mathsOk -and $readingOk)

# ---- NOTES: scoped push-context works without Home state
$n = Turn "s" @(@{ tool = "createNote"; input = @{ title = "TTS research"; content = "text to speech project notes"; tags = @("tts") } }) $null "p2n"
$noteId = $n.stepRecords[0].resultSummary.id
$ctx = @{ surface = "scoped:notes"; visibleIds = @($noteId); selectedId = $noteId }
$body = @{ context = $ctx; instruction = "Find related notes"; plan = @{ steps = @(@{ tool = "searchNotes"; input = @{ query = "tts" } }) }; sessionId = "p2n-notes" }
$found = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body | ConvertTo-Json -Depth 10) -Headers $h
Check "notes: scoped search executes inline" ($found.status -eq "executed" -and $found.ui -eq "inline")
$hist = Invoke-RestMethod -Uri "$base/api/history?sessionId=p2n-notes"
Check "notes: thread recorded under scoped surface" ($hist.turns.Count -ge 1)
$homeHist = Invoke-RestMethod -Uri "$base/api/history?sessionId=live5"
Check "notes: home session untouched by scoped turns" ($homeHist.turns.Count -eq 0)

Write-Output "FAILURES: $fail"
exit $fail

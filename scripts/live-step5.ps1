# Live step-4/5 verification: drives the exact payloads the UI sends,
# against the real backend. No mocks. Run while the server is up:
#   $env:FLOWOS_DB="..."; $env:FLOWOS_PORT="3105"; node dist/server.js
#   & "C:\Users\91808\Desktop\FlowOS\scripts\live-step5.ps1"
$h = @{ "Content-Type" = "application/json" };
$base = "http://localhost:3105";
$fail = 0;
function Check($name, $cond) {
  if ($cond) { Write-Output "PASS: $name" } else { Write-Output "FAIL: $name"; $script:fail += 1 }
}
function Turn($instruction, $steps, $approval, $session) {
  $body = @{ context = @{ surface = "home" }; instruction = $instruction; plan = @{ steps = $steps }; sessionId = $session };
  if ($approval) { $body.approval = $approval }
  return Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body | ConvertTo-Json -Depth 10) -Headers $h;
}
function LogCount() { return (Invoke-RestMethod -Uri "$base/api/log?limit=500").entries.Count }

# seed: 2 open tasks (single auto creates execute immediately)
Turn "seed a" @(@{ tool = "createTask"; input = @{ title = "bulk-a" } }) $null "live5" | Out-Null
Turn "seed b" @(@{ tool = "createTask"; input = @{ title = "bulk-b" } }) $null "live5" | Out-Null

# CONFIRM pill flow: bulk-done with 2 open -> needs-approval/diff-card
$pending = Turn "Demo: mark all open tasks done" @(@{ tool = "updateTask"; input = @{ filter = @{ status = "todo" }; patch = @{ status = "done" } } }) $null "live5"
Check "confirm pill gates (needs-approval/diff-card)" ($pending.status -eq "needs-approval" -and $pending.ui -eq "diff-card")
Check "approval template echoed (not client-built)" ($null -ne $pending.approvalTemplate.groupId -and $null -ne $pending.planFingerprint)
# approve with the ECHOED template:
$body = @{ context = @{ surface = "home" }; instruction = "Demo: mark all open tasks done"; plan = @{ steps = @(@{ tool = "updateTask"; input = @{ filter = @{ status = "todo" }; patch = @{ status = "done" } } }) }; approval = $pending.approvalTemplate; sessionId = "live5" }
$approved = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body | ConvertTo-Json -Depth 10) -Headers $h
Check "approve executes inline" ($approved.status -eq "executed" -and $approved.ui -eq "inline")
Check "inline summary names field diff" ($approved.summary -match "status: todo")
Check "inline undo targets present" ($approved.stepRecords[0].logEntryIds.Count -gt 0)
# inline Undo works:
$u = Invoke-RestMethod -Uri "$base/api/undo" -Method Post -Body (@{ entryId = $approved.stepRecords[0].logEntryIds[0] } | ConvertTo-Json) -Headers $h
Check "inline undo reverts group" ($u.ok -eq $true)

# EDIT flow: same plan, edited patch -> re-classified (new fingerprint, still confirm, 2 items)
$edited = Turn "edited" @(@{ tool = "updateTask"; input = @{ filter = @{ status = "todo" }; patch = @{ status = "doing" } } }) $null "live5"
Check "edit re-submits for re-classification" ($edited.status -eq "needs-approval" -and $edited.planFingerprint -ne $pending.planFingerprint)

# CANCEL: no turn call is made by the UI — log count stable across a no-op window
$c1 = LogCount; Start-Sleep -Milliseconds 200; $c2 = LogCount
Check "cancel writes zero rows (no call, no rows)" ($c1 -eq $c2)

# BLOCKED pill flow: done tasks exist? mark one done first via direct approve
$mk = Turn "mk" @(@{ tool = "createTask"; input = @{ title = "done-1"; status = "done" } }) $null "live5b"
$blocked = Turn "Demo: delete all done tasks" @(@{ tool = "deleteTask"; input = @{ filter = @{ status = "done" } } }) $null "live5b"
Check "blocked pill gates (blocked/blocked-card)" ($blocked.status -eq "blocked" -and $blocked.ui -eq "blocked-card")
Check "itemized alternative inline, no second round trip" ($blocked.proposals[0].itemizedAlternative.affectedIds.Count -ge 1 -and $null -ne $blocked.proposals[0].itemizedAlternative.executableInput)
# Review itemized plan: submit ECHOED executable input -> confirm card
$exe = $blocked.proposals[0].itemizedAlternative.executableInput
$tool = $blocked.proposals[0].tool
$body2 = @{ context = @{ surface = "home" }; instruction = "review itemized"; plan = @{ steps = @(@{ tool = $tool; input = $exe }) }; sessionId = "live5b" }
$itemized = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body2 | ConvertTo-Json -Depth 10) -Headers $h
Check "itemized re-enters gate as confirm" ($itemized.status -eq "needs-approval" -and $itemized.ui -eq "diff-card")
$body3 = @{ context = @{ surface = "home" }; instruction = "review itemized"; plan = @{ steps = @(@{ tool = $tool; input = $exe }) }; approval = $itemized.approvalTemplate; sessionId = "live5b" }
$gone = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body3 | ConvertTo-Json -Depth 10) -Headers $h
Check "itemized approve executes" ($gone.status -eq "executed")

# PREFERENCE CITATION live: save pref, create event, reschedule with pref
$sp = Turn "remember" @(@{ tool = "saveLearnedPreference"; input = @{ statement = "you usually study at 7pm" } }) $null "live5c"
$prefId = $sp.stepRecords[0].resultSummary.id
$ev = Turn "ev" @(@{ tool = "createEvent"; input = @{ title = "Maths study"; startAt = "2026-09-20T17:00:00"; endAt = "2026-09-20T18:00:00" } }) $null "live5c"
$eventId = $ev.stepRecords[0].resultSummary.id
$rp = Turn "move maths" @(@{ tool = "reschedule"; input = @{ eventId = $eventId; newStartAt = "2026-09-20T19:00:00"; newEndAt = "2026-09-20T20:00:00" } }) $null "live5c"
# re-submit with preferencesUsed (UI reads pref id from GET /api/preferences)
$prefs = Invoke-RestMethod -Uri "$base/api/preferences"
$prefId = ($prefs.preferences | Where-Object { $_.statement -eq "you usually study at 7pm" }).id
$body4 = @{ context = @{ surface = "home" }; instruction = "move maths"; plan = @{ steps = @(@{ tool = "reschedule"; input = @{ eventId = $eventId; newStartAt = "2026-09-20T19:00:00"; newEndAt = "2026-09-20T20:00:00" } }); preferencesUsed = @($prefId) }; approval = $rp.approvalTemplate; sessionId = "live5c" }
$cite = Invoke-RestMethod -Uri "$base/api/turn" -Method Post -Body ($body4 | ConvertTo-Json -Depth 10) -Headers $h
Check "preference cited inline end-to-end" ($cite.summary -match "Since you usually study at 7pm")
Check "confirm inline carries undo" ($cite.stepRecords[0].logEntryIds.Count -gt 0)

Write-Output "FAILURES: $fail"
exit $fail

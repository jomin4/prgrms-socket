<#
.SYNOPSIS
  챕터 하나가 끝났을 때 add → commit → tag → push 를 한 번에 처리한다.

.EXAMPLE
  pwsh scripts/chapter-done.ps1 -Chapter 3 -Title "채팅 메시지 API"
  pwsh scripts/chapter-done.ps1 -Chapter 3 -Title "쓰로틀링 재작업" -NoTag
#>
param(
  [Parameter(Mandatory = $true)][int]$Chapter,
  [Parameter(Mandatory = $true)][string]$Title,
  [switch]$NoTag
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$tag = 'ch{0:d2}' -f $Chapter
$msg = '{0}: {1}' -f $tag, $Title

# 1. 스테이징
git add -A

# 2. 변경사항이 있을 때만 커밋
$staged = git diff --cached --name-only
if ([string]::IsNullOrWhiteSpace($staged)) {
  Write-Host "[skip] 커밋할 변경사항이 없습니다." -ForegroundColor Yellow
}
else {
  Write-Host "[commit] $msg" -ForegroundColor Cyan
  ($staged -split "`n") | Where-Object { $_ } | ForEach-Object { Write-Host "         + $_" }
  git commit -m $msg
}

# 3. 태그 (이미 있으면 이동)
if (-not $NoTag) {
  $exists = git tag --list $tag
  if ($exists) {
    Write-Host "[tag] $tag 이미 존재 -> 현재 커밋으로 이동" -ForegroundColor Yellow
    git tag -f -a $tag -m $msg | Out-Null
  }
  else {
    Write-Host "[tag] $tag" -ForegroundColor Cyan
    git tag -a $tag -m $msg | Out-Null
  }
}

# 4. 푸시
$branch = git rev-parse --abbrev-ref HEAD
Write-Host "[push] origin/$branch" -ForegroundColor Cyan
git push origin $branch

if (-not $NoTag) {
  git push origin $tag --force
}

Write-Host ""
Write-Host "[done] $msg -> pushed" -ForegroundColor Green
git log --oneline -1

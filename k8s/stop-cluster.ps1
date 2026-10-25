# RewaBank cluster stop script — run this BEFORE stopping Docker Desktop
k3d cluster stop rewabank
Write-Host "Cluster stopped. Safe to shut down Docker Desktop." -ForegroundColor Green

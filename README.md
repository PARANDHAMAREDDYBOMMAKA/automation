# Kalvium Worklog Automation - Fully Automated

Automatically submit your Kalvium worklog every weekday at 5:00 PM IST. Configure once, forget forever.

## Features

- Web UI to configure authentication cookies
- Automatic daily submission at 5:00 PM IST (Monday-Friday)
- Automatic deployment via GitHub Actions
- No database required - uses file storage
- Deployed on Render.com (free tier)

## Complete Setup Guide

### Step 1: Fork and Push to GitHub

```bash
cd /Users/parandhamareddybommaka/Desktop/automation
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/kalvium-worklog-automation.git
git push -u origin main
```

### Step 2: Deploy to Render.com (Automatic)

1. Go to [render.com](https://render.com) and sign up/login
2. Click **New +** → **Blueprint**
3. Connect your GitHub repository
4. Click **Apply** - Render will automatically:
   - Build the Docker image
   - Deploy the application
   - Set up persistent storage for config
   - Give you a public URL

### Step 3: Configure Your Cookies (One Time Only)

1. Open your deployed app URL (e.g., `https://kalvium-worklog-automation.onrender.com`)

2. Get your Kalvium cookies:
   - Open kalvium.community in Chrome
   - Login with Google
   - Press **F12** → **Application** tab → **Cookies** → kalvium.community
   - Copy these 3 cookies:
     - `AUTH_SESSION_ID`
     - `KEYCLOAK_IDENTITY`
     - `KEYCLOAK_SESSION`

3. Paste cookies in the web form and click **Save Configuration**

4. Click **Run Now** to test

### Step 4: Done!

That's it! The automation will now run every weekday at 5:00 PM IST automatically.

## How It Works

- **Scheduled Task**: Runs Mon-Fri at 11:30 AM UTC (5:00 PM IST)
- **Storage**: Cookies saved in `/app/data/config.json` on persistent disk
- **Deployment**: GitHub Actions triggers Render.com deployment on every push

## Updating

Push any changes to GitHub - automatic redeployment:

```bash
git add .
git commit -m "Update"
git push
```

## Monitoring

Check logs on Render.com dashboard → Logs tab to see scheduled runs.

## Free Tier

- Render.com free tier works perfectly
- App may sleep after inactivity but wakes for scheduled tasks
- 1GB persistent storage included

## Troubleshooting

**Cookies expired?**
- Login to kalvium.community again
- Get fresh cookies
- Update in web UI

**Not running at 5 PM?**
- Check Render logs at 11:30 AM UTC
- Verify timezone settings

**Build failed?**
- Check GitHub Actions tab
- Verify all files committed

## Architecture

```
GitHub → GitHub Actions → Render.com → Docker → Spring Boot → Selenium → Kalvium
```

## Cost

$0/month - everything runs on free tiers!

---
author:
- name: Kira Howe
  url: https://kiramclean.com
type: post
title: How To Set Up Your Own Nextcloud Server
date: 2020-12-19
site/group: Tech
site/tags:
  - ungoogle
  - nextcloud
---

One of my goals for 2020 was to stop using Google's products. There are a lot of reasons why, but that's not the point of this post. I found out about Nextcloud last month and it turns out it's a great replacement for a lot of Google. I don't actually use all of its features, but I've migrated my calendar, reminders, contacts, bookmarks, video calls, photos, and news feeds and I'm really happy with it so far.

There are a lot of companies that will host Nextcloud for you where you just sign up for an account like anything else, but in case you're interested in hosting Nextcloud for yourself this post is basically a brain dump of how I did that. It took me a while to cobble together all the pieces I needed to get everything working from end to end, so I'm hoping this might save someone else from having to do the same. If you know your way around servers and Nextcloud already you can just skim the headings like a checklist to make sure you don't forget an important step. But if you want a succinct overview of the actual steps I did and commands I ran, each section contains those details. By the end you'll see how I installed Nextcloud on my own server, secured it, set up backups, and set up external storage for my photos.

Some parts are pieced together from other partial guides or longer blog posts, so where relevant the references lead to those sources. This post is more of a quick start with just the essential steps. Anything in `<pointy-brackets>` is meant to be replaced. So the actual command I ran was e.g. `adduser kira`, not `adduser <name>`.

## What this list assumes you already have

- A domain name. I got mine from [Namecheap](https://namecheap.com).
- An account with a cloud server provider. I use [Linode](https://www.linode.com/choosing-linode/).
- An account with [Backblaze](https://www.backblaze.com/b2/cloud-storage.html)
- An account with [Healthchecks.io](https://healthchecks.io/)
- Your ssh key
- $99/year. I pay $5/month for the server I use, $2/month for back ups, $5/year for the domain name ($5.16 actually), and CAD$10/year for carbon offsets.

## 1. Set up a server running Ubuntu and set up ssh access for yourself

- Spin up a new server with your cloud provider. I use a "nanode", the smallest server available from Linode, with 1GB of RAM and 25GB of storage, which is plenty more than [Nextcloud's minimum specs](https://docs.nextcloud.com/server/16/admin_manual/installation/system_requirements.html#server).
- Select an operating system that can install [snap packages](https://snapcraft.io/docs/installing-snapd). I'm using Ubuntu 20.04 (LTS). Nextcloud recommends at least either Ubuntu 18.04 LTS or Red Hat Enterprise Linux 7.
- Add your ssh key to the server. There should be a way to do this through the UI where you manage your new server.
- Copy the IP address of your new server

### 1.1. ssh into your server as the root user and make yourself a new sudo user that can also ssh into the machine <sup>[1](https://www.digitalocean.com/community/tutorials/initial-server-setup-with-ubuntu-20-04)</sup>

- `ssh root@<server-ip-address>`
- `adduser <name>`
- `usermod -aG sudo <name>`
- `rsync --archive --chown=<name>:<name> ~/.ssh /home/<name>`

Close this ssh session and log in as your new user to make sure it works:

- `logout`
- `ssh <name>@<server-ip-address>`

Leave this ssh session open. The rest of the commands below are meant to be run on your server, unless otherwise stated.

## 2. Point your new server to your custom domain

There should be a way to do this in the admin section for your server. On Linode's there's a "Domains" section in the left admin menu. From there I clicked "Add a Domain" in the top right, then filled in the domain name, my email address, and selected "Insert default records from one of my Linodes" from the "Insert Defaults Records" dropdown, then I selected my new Nextcloud server from the list of Linodes. The steps might be slightly different depending what cloud server provider you're using. By the end you need DNS records pointing your domain name to your Nextcloud server. If you did this through Linode (or whatever you're using), you'll also need to update the nameservers with your domain registar.

## 3. Set up a basic firewall

- `sudo ufw allow OpenSSH`
- `sudo ufw allow https`
- `sudo ufw allow http`
- `sudo ufw enable`

## 4. Install Nextcloud

- `sudo snap install nextcloud`
- `sudo nextcloud.manual-install <username> <password>`

## 5. Set up your domain, enable https, and install an auto-updating certificate from Let's Encrypt

- `sudo nextcloud.occ config:system:set trusted_domains 1 --value=<your-domain.name>`
- `sudo nextcloud.enable-https lets-encrypt`

## 6. Enable 2FA on your Nextcloud account <sup>*</sup>

- Log in to your new personal cloud at the domain you configured using the username and password you chose above and install the 2FA app
    - Click on your initial in the top right corner of the Nextcloud dashboard and select "Apps"
    - In the left side bar click on "Security", then search for the "Two-Factor TOTP Provider" app
    - Click "Download and enable"
- Set up 2FA with this newly installed app
    - Click on your initial in the top right corner and select "Settings"
    - In the left sidebar, click on "Security" (in the "Personal" section) then check the "Enable TOTP" box and follow the instructions to set up 2FA

I managed to forget my password in the time between installing Nextcloud and trying to log in for the first time. If that happens to you, you can reset it by running `sudo nextcloud.occ user:resetpassword <username>`.

---

<small>* Doing this means you will be required to generate "app passwords" in order to log in to your Nextcloud account in third party apps or other devices (to use Nextcloud to sync your calendar or reminders to your phone, for example.) There's a tiny box with a button that says "Create new app password" at the bottom of the "Security" admin section (under "Personal", not "Administration") where you can do that.</small>

## 7. Set up backups

### 7.1. Turn on "local" backups

- Enable backups for your whole server for a first layer of backups. I did this when I was setting up my Linode (there was a checkbox in the "Optional Add-ons" section for it). Otherwise there's a "Backups" tab in the admin section where you can turn them on. Linode charges $2/month for this.

### 7.2. Set up "offsite" backups

- Install and set up Backblaze
  - Make a bucket in Backblaze for your backups
  - Make an app key with access to your backup bucket
  - Get the Backblaze cli and configure it
    - `sudo apt install python3-pip`
    - `sudo pip3 install b2`
    - `sudo b2 authorize_account <keyID>`
    - Copy the key secret from the app key you just made to authorize the Backblaze cli

- Create a new user to run the backups and disable password access for it, for security <sup>[2](https://kevq.uk/how-to-backup-nextcloud/)</sup>
    - `sudo adduser ncbackup`
    - `sudo usermod -s /sbin/nologin ncbackup` <sup>*</sup>
- Create directories for the backups and logs
    - `sudo mkdir -p /home/ncbackup/backups/logs`
- Create the backup script and make it runnable <sup>**</sup>
    - `sudo touch /usr/sbin/ncbackup.sh`
    - `sudo chmod +x /usr/sbin/ncbackup.sh`
    - `sudo vim /usr/sbin/ncbackup.sh` and copy the contents of the backup script below into your new file, or write your own that accomplishes the same things: <sup>***</sup>

```bash
#!/bin/bash
set -e

DATE=$(date '+%Y-%m-%d')

# Output to a logfile
exec &> /home/ncbackup/backups/logs/${DATE}.txt

# Export all your config and data from Nextcloud
echo "Starting Nextcloud export..."
nextcloud.export
echo "Export complete"

# Compress backed up folder
echo "Compressing backup..."
tar -zcf /home/ncbackup/backups/${DATE}.tar.gz -C /var/snap/nextcloud/common/backups/ .
echo "Nextcloud backup successfully compressed to /home/ncbackup/backups"

# Remove uncompressed backup data
rm -rf /var/snap/nextcloud/common/backups/*

# Remove backups and logs older than 5 days
echo "Removing backups older than 5 days..."
find /home/ncbackup/backups -type f -mtime +5 -delete
find /home/ncbackup/backups/logs -type f -mtime +5 -delete

# Keep 14 days of backups in Backblaze
echo "Uploading to Backblaze..."
b2 sync --keepDays 14 --replaceNewer /home/ncbackup/backups b2://<your-bucket-name>
echo "Nextcloud backup completed successfully"
```

- Let the `ncbackup` user run the backup script as the root user
  - `sudo visudo`
  - Copy this to the end of the file that opens:

```bash
# Allow ncbackup to run script as sudo
ncbackup ALL=(ALL) NOPASSWD: /usr/sbin/ncbackup.sh
```

---

<p>
  <small>
  * If you want to undo this for some reason you can run <code>sudo usermod -s /bin/bash ncbackup</code>
  </small>
</p>
<p>
  <small>
  ** Note this means you will have 6 copies of all your data on your server all the time -- 5 backups and the live versions. The backups are compressed, but it can still add up to a lot of space. Keep an eye on how much storage your server is using. Running it out of space will probably be one of the first issues you run into. I explain how to get notified when that's close to happening at the end.
  </small>
</p>
<p>
  <small>
  *** You don't have to use vim here. Your server probably has nano installed or you can install the editor of your choice. To change the default editor on your server, run <code>sudo update-alternatives --config editor</code>, and choose the one you want.
  </small>
</p>

## 8. Schedule and monitor your backups

- Make yourself a healthcheck at [healthchecks.io](https://healthchecks.io) and copy the ping url
- `sudo crontab -u ncbackup -e`
- Copy this to the bottom of the file: `0 2 * * * sudo /usr/sbin/ncbackup.sh && curl -fsS -m 10 --retry 5 -o /dev/null <your-ping-url>`

This will run your backups once per day at 2am (in your server's timezone, probably UTC), but you can set whatever time and frequency you want, just remember to update your healthcheck to match.


## 9. Test your backups

Backups are only useful if you can use them to restore your data. Make sure yours work before you need them.

To test your entire server backups you can just try restoring the whole server using Linode's (or whoever's) UI. Testing the archived backups we uploaded to Backblaze is a little more involved but you'll be glad you know how to do it when you need it.

- Repeat steps 1-5, except you can just update the records for your domain that's already set up to point to your new server's IP address(es).
- Download one of your backups
- Copy the backup onto your new server. Run this in a terminal on your machine (not in an ssh session with a remote server):
  - `scp /local/path/to/your/backup/ <user>@<new-server-ip-address>:~`

*ssh into your new server for the rest of these commands*
- Unzip, rename, and move the backup to a place where the Nextcloud snap installation will be able to access it, then make the root user the owner
  - `tar -xvzf <backup-name>.tar.gz`
  - `sudo mv <backup-data-dir>/ /var/snap/nextcloud/current/`
  - `sudo chown -R root:root /var/snap/nextcloud/current/<backup-data-dir>/`
- Import your data
  - `sudo nextcloud.import /var/snap/nextcloud/current/<backup-data-dir>/`
- Once it's done, clean up the backup archive
  - `rm <backup-name>.tar.gz`

This should be all you need to restore your Nextcloud installation. It might take a while for the DNS records to propagate, so if you want to test that your restored cloud is working in the meantime you can check it directly at its IP address if you add that to the list of trusted domains:
- `sudo nextcloud.occ config:system:set trusted_domains 2 --value=<new-server-ip-address>`

Note this will only be available over http, so you might get a dramatic warning about security when you visit the ip address directly. To remove the ip address from the list of trusted domains once you're satisfied, run:
- `sudo nextcloud.occ config:system:delete trusted_domains 2`

## 10. Offset your CO<sub>2</sub>

It's not going to be clear exactly what the environmental impact of your server is, but it won't be nothing. You can get a rough idea how much CO<sub>2</sub> your server emits with tools like [this one](https://www.websitecarbon.com/). Then you can buy carbon offsets from a reputable carbon offset vendor, like [Less](https://www.less.ca/en-ca/tonnes.cfm). I spent $10/year to offset half a tonne of CO<sub>2</sub>.

I know carbon offsetting is a [long and complicated topic](https://davidsuzuki.org/wp-content/uploads/2019/10/purchasing-carbon-offsets-guide-for-canadians.pdf), and the environmental impact of computing infrastructure goes way beyond CO<sub>2</sub> emissions, but the point is just to be aware that doing all this stuff on your computer has potentially negative consequences in the real world and to at least try to minimize them where you can and mitigate them where you can't.

## Bonus

### Set up a Backblaze bucket as external storage, e.g. for photos

- Install and enable the "External storage support" app for your Nextcloud instance
- Go to "Settings" then, under "Administration" in the left side bar (_not_ under "Personal"), click "External storages"
- Enter a name for your new folder<sup>*</sup> and select "Amazon S3" from the "Add storage" dropdown, then fill in the details for your Backblaze bucket and account

<p>
<small>
* Make sure the name you give the external storage folder isn't already taken. I called mine "Photos", which already existed in my Nextcloud files, and it conflicted in strange and surprising ways. If you want to call your external storage folder "Photos" make sure to go delete the "Photos" folder that's already there first.
</small>
</p>

### Get notified when you're approaching your storage limit

If you choose the cheapest Linode server like I did it doesn't come with much storage, and depending on how much data you have and how many backups you're leaving on the server you might run it out of storage pretty quickly. There's an app called "Quota warning" in the monitoring category you can install to get notified if you're approaching your server's storage limits. You can configure when and how it notifies you in "Additional settings" after it's installed.

---

That's it! I hope this helps someone avoid hours of searching through documentation, blog posts, and outdated forums. Good luck!

**Discuss this post on [Hacker News](https://news.ycombinator.com/item?id=25481465), [Dev.to](https://dev.to/kiraemclean/how-to-set-up-your-own-nextcloud-server-2hc3) or [Reddit](https://www.reddit.com/r/NextCloud/comments/kgdpaw/i_set_up_my_own_nextcloud_instance_as_part_of_my/)**

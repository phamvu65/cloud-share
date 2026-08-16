# VPS provisioning (Ansible)

Reproduces the manual VPS setup (Docker, nginx, certbot, ufw) as code. This is
for *provisioning* the box — app deploys still go through `.github/workflows/deploy.yml`
via the separate `cloudshare_deploy` key, untouched by this.

## One-time setup

1. Install Ansible locally: `pip install ansible` (or `pipx install ansible`).
2. Install the required collection: `ansible-galaxy collection install -r requirements.yml`
3. Generate a dedicated keypair for this (don't reuse your personal key or `cloudshare_deploy`):
   ```
   ssh-keygen -t ed25519 -C "ansible-provisioning" -f ~/.ssh/cloudshare_ansible
   ```
4. Add the public key to the VPS's `deploy` user (it already has sudo):
   ```
   ssh-copy-id -i ~/.ssh/cloudshare_ansible.pub deploy@<VPS_HOST>
   ```
5. Copy the inventory template and fill in the real host:
   ```
   cp inventory.ini.example inventory.ini
   ```
   Edit `inventory.ini` — set `ansible_host` to the real VPS IP/hostname.

## Running it

Dry run first — shows what would change without touching anything:
```
ansible-playbook site.yml --check --diff --ask-become-pass
```

Then for real:
```
ansible-playbook site.yml --diff --ask-become-pass
```

`--ask-become-pass` prompts for `deploy`'s sudo password interactively (same
password `sudo certbot certificates` asked you for) — nothing is stored.

## What it does / doesn't do

- Installs Docker Engine + Compose plugin, puts `deploy` in the `docker` group.
- Installs nginx, creates one vhost per domain in `group_vars/vps.yml`.
- Installs certbot, obtains/wires up Let's Encrypt certs per domain.
- Removes the `cloudshareapi` legacy vhost file (had `server_name vuxph.xyz`
  but was serving the `filestation.vuxph.xyz` cert — see git history for detail).
- Sets ufw to match the current live rules (22/80/443, deny incoming by default).
- Does **not** clone the repo, write `.env`, or `docker login` to GHCR — those
  involve secrets and are still manual one-time steps on a fresh box.

Safe to re-run: every task is idempotent, so running this again against the
already-configured VPS should report no changes (or only the intended diff).

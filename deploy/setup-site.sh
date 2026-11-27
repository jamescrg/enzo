#!/usr/bin/env bash
#
# setup-site.sh: put Quiet on https://quiettimer.app, on the prod droplet.
#
#     sudo /home/james/tacet/deploy/setup-site.sh
#
# Each step is skipped when already done, so rerunning is safe:
#   1. A Let's Encrypt certificate for quiettimer.app and www.quiettimer.app, by the nginx
#      plugin like the droplet's other certificates. A plain-http site is put up first for
#      the challenge to be answered on.
#   2. The nginx site from deploy/nginx-quiettimer, enabled.
#   3. nginx -t (the site removed again if it fails), reload, checks.
#
# The app is the checkout's web/ folder, served as it stands: to update it,
#     git -C /home/james/tacet pull

set -euo pipefail

DOMAIN=quiettimer.app
WEB=/home/james/tacet/web
SITE_SRC=/home/james/tacet/deploy/nginx-quiettimer
SITE=/etc/nginx/sites-available/quiettimer
SITE_LINK=/etc/nginx/sites-enabled/quiettimer
LINEAGE=/etc/letsencrypt/live/$DOMAIN

say()  { printf '\n==> %s\n' "$*"; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || fail "run with sudo"
[[ -f $SITE_SRC ]] || fail "$SITE_SRC missing"
[[ -f $WEB/index.html ]] || fail "$WEB/index.html missing"
[[ -f /etc/nginx/snippets/security-headers.conf ]] || fail "snippets/security-headers.conf missing"
sudo -u www-data test -r "$WEB/index.html" || fail "nginx (www-data) cannot read $WEB"
for name in "$DOMAIN" "www.$DOMAIN"; do
    getent hosts "$name" >/dev/null || fail "$name does not resolve yet"
done

# --- 1. certificate ---------------------------------------------------------
say "certificate"
if [[ -f $LINEAGE/fullchain.pem ]]; then
    echo "already have $LINEAGE"
else
    cat > "$SITE" <<BOOT
# Stands in until the certificate exists; replaced by deploy/nginx-quiettimer.
server {
    listen 80;
    server_name $DOMAIN www.$DOMAIN;
    root $WEB;
}
BOOT
    ln -sfn "$SITE" "$SITE_LINK"
    if ! nginx -t; then
        rm -f "$SITE_LINK" "$SITE"
        fail "nginx -t failed; removed the new site"
    fi
    systemctl reload nginx
    if ! certbot certonly --non-interactive --agree-tos --nginx \
            --cert-name "$DOMAIN" -d "$DOMAIN" -d "www.$DOMAIN"; then
        rm -f "$SITE_LINK" "$SITE"
        systemctl reload nginx
        fail "certbot failed; removed the new site"
    fi
    [[ -f $LINEAGE/fullchain.pem ]] || fail "certbot did not produce $LINEAGE"
fi

# --- 2. nginx ---------------------------------------------------------------
say "nginx"
install -m 644 -o root -g root "$SITE_SRC" "$SITE"
ln -sfn "$SITE" "$SITE_LINK"
if ! nginx -t; then
    rm -f "$SITE_LINK"
    fail "nginx -t failed; removed the new site"
fi
systemctl reload nginx
echo "enabled $SITE_LINK"

# --- 3. checks --------------------------------------------------------------
say "checks"
get() { curl -sS -o /dev/null -w "$2" --resolve "$DOMAIN:443:127.0.0.1" --resolve "www.$DOMAIN:443:127.0.0.1" "$1" 2>/dev/null || true; }
code=000
for _ in 1 2 3 4 5; do
    code=$(get "https://$DOMAIN/" '%{http_code}')
    [[ $code == 200 ]] && break
    sleep 1
done
[[ $code == 200 ]] || fail "https://$DOMAIN/ answered $code"
for file in sw.js js/app.js audio/bell_chapel.mp3 img/moon.png; do
    code=$(get "https://$DOMAIN/$file" '%{http_code}')
    [[ $code == 200 ]] || fail "https://$DOMAIN/$file answered $code"
done
type=$(get "https://$DOMAIN/manifest.webmanifest" '%{content_type}')
[[ $type == application/manifest+json ]] || fail "the manifest is served as '$type'"
to=$(get "https://www.$DOMAIN/" '%{redirect_url}')
[[ $to == "https://$DOMAIN/" ]] || fail "www goes to '$to'"
echo "https://$DOMAIN answers, and www goes to it"

printf '\nDone. https://%s is up.\n' "$DOMAIN"

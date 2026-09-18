#!/bin/bash
#
# The contents of this file are subject to the license and copyright
# detailed in the LICENSE and NOTICE files at the root of the source
# tree and available online at
#
# http://www.dspace.org/license/
#

# Start the Shibboleth daemon and Apache in the foreground.
set -eu

escape_sed_replacement() {
    printf '%s' "$1" | sed 's/[&|\\]/\\&/g'
}

if [ -f /etc/shibboleth/shibboleth2.xml.template ]; then
    : "${APACHE_SERVER_NAME:?APACHE_SERVER_NAME must be set}"
    : "${SHIBBOLETH_ENTITY_ID:?SHIBBOLETH_ENTITY_ID must be set}"
    : "${SHIBBOLETH_HOME_URL:?SHIBBOLETH_HOME_URL must be set}"
    : "${SHIBBOLETH_DISCOVERY_URL:?SHIBBOLETH_DISCOVERY_URL must be set}"
    : "${SHIBBOLETH_HANDLER_SSL:?SHIBBOLETH_HANDLER_SSL must be set}"
    : "${SHIBBOLETH_COOKIE_PROPS:?SHIBBOLETH_COOKIE_PROPS must be set}"
    : "${SHIBBOLETH_SUPPORT_CONTACT:?SHIBBOLETH_SUPPORT_CONTACT must be set}"

    server_name=$(escape_sed_replacement "$APACHE_SERVER_NAME")
    entity_id=$(escape_sed_replacement "$SHIBBOLETH_ENTITY_ID")
    home_url=$(escape_sed_replacement "$SHIBBOLETH_HOME_URL")
    discovery_url=$(escape_sed_replacement "$SHIBBOLETH_DISCOVERY_URL")
    handler_ssl=$(escape_sed_replacement "$SHIBBOLETH_HANDLER_SSL")
    cookie_props=$(escape_sed_replacement "$SHIBBOLETH_COOKIE_PROPS")
    support_contact=$(escape_sed_replacement "$SHIBBOLETH_SUPPORT_CONTACT")

    sed \
        -e "s|\${APACHE_SERVER_NAME}|$server_name|g" \
        -e "s|\${SHIBBOLETH_ENTITY_ID}|$entity_id|g" \
        -e "s|\${SHIBBOLETH_HOME_URL}|$home_url|g" \
        -e "s|\${SHIBBOLETH_DISCOVERY_URL}|$discovery_url|g" \
        -e "s|\${SHIBBOLETH_HANDLER_SSL}|$handler_ssl|g" \
        -e "s|\${SHIBBOLETH_COOKIE_PROPS}|$cookie_props|g" \
        -e "s|\${SHIBBOLETH_SUPPORT_CONTACT}|$support_contact|g" \
        /etc/shibboleth/shibboleth2.xml.template > /etc/shibboleth/shibboleth2.xml

    test -r /run/shibboleth-source/sp-key.pem
    test -r /run/shibboleth-source/sp-cert.pem
    install -o _shibd -g _shibd -m 0600 /run/shibboleth-source/sp-key.pem /etc/shibboleth/sp-key.pem
    install -o _shibd -g _shibd -m 0644 /run/shibboleth-source/sp-cert.pem /etc/shibboleth/sp-cert.pem
else
    # Preserve the upstream development-image behavior.
    sed -i "s/\${APACHE_SERVER_NAME}/$APACHE_SERVER_NAME/g" /etc/shibboleth/shibboleth2.xml
fi

# Refuse to accept traffic with an invalid SP configuration or credential pair.
shibd -t

/etc/init.d/shibd start

rm -f /var/run/apache2/apache2.pid

exec /usr/sbin/apache2ctl -DFOREGROUND

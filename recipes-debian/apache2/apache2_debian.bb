SUMMARY = "Apache HTTP Server"
DESCRIPTION = "The Apache HTTP Server Project's goal is to build a secure, efficient and \
extensible HTTP server as standards-compliant open source software. The \
result has long been the number one web server on the Internet. \
Installing this package results in a full installation, including the \
configuration files, init scripts and support scripts."

PR = "r0"

inherit debian-package

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=dbff5a2b542fa58854455bf1a0b94b83"

DEPENDS = " \
    libtool-native dpkg-native apache2-native \
    openssl expat pcre apr apr-util \
"

inherit autotools pkgconfig perlnative

# Base on
# meta-openembedded/meta-webserver/recipes-httpd/apache2
# server-makefile.patch:
#	Run gen_test_char from apache2-native instead of apache2
#	to avoid exec format error.
SRC_URI += " \
    file://server-makefile.patch \
"

# Configure follow debian/rules
EXTRA_OECONF = " \
    --enable-layout=Debian --enable-so --with-program-name=${DPN} \
    --enable-suexec --with-suexec-caller=www-data \
    --with-suexec-bin=${libexecdir}/suexec --with-suexec-docroot=${localstatedir}/www \
    --with-suexec-userdir=public_html \
    --with-suexec-logfile=${localstatedir}/log/apache2/suexec.log \
    --with-suexec-uidmin=100 --enable-suexec=shared --enable-log-config=static \
    --with-apr=${STAGING_BINDIR_CROSS}/apr-1-config \
    --with-apr-util=${STAGING_BINDIR_CROSS}/apu-1-config \
    --enable-pie \
    --enable-mpms-shared=all \
    --enable-mods-shared="all cgi ident authnz_fcgi" \
    --enable-mods-static="unixd logio watchdog version" \
    CFLAGS="-pipe ${CFLAGS}" CPPFLAGS="-DPLATFORM='\"Debian\"' ${CPPFLAGS}" \
    LDFLAGS="-Wl,--as-needed ${LDFLAGS}" LTFLAGS="--no-silent" \
"

# Correct installation and library path.
# Option ap_cv_void_ptr_lt_long=no:
# 	Avoid error: Size of "void *" is less than size of "long"
EXTRA_OECONF += " \
    --prefix=${base_prefix} \
    --localstatedir=${localstatedir}/${DPN} \
    --includedir=${includedir}/${DPN} \
    --datadir=${datadir}/${DPN} \
    --sysconfdir=${sysconfdir}/${DPN} \
    --libexecdir=${libexecdir}/modules \
    --enable-ssl \
    --with-ssl=${STAGING_LIBDIR}/.. \
    --with-expat=${STAGING_LIBDIR}/.. \
    ap_cv_void_ptr_lt_long=no \
"
CFLAGS_prepend = " -I${STAGING_INCDIR}/openssl "

do_debian_patch_append() {
	# Apply patch for suexec
	cp ${S}/support/suexec.c ${S}/support/suexec-custom.c
	patch -p1 -i ${S}/debian/patches/suexec-custom.patch
}

do_install_append() {
	SERVER_VERSION=$(dpkg-parsechangelog -l${S}/debian/changelog | \
		perl -ne 'print $1 if m/Version:\s*([\d\.]+)/')
	MODULE_DIR="${libexecdir}/modules/"
	API=$(perl -ne 'print $1 if m/define\s+MODULE_MAGIC_NUMBER_MAJOR\s+?(.*)$/' \
		< ${S}/include/ap_mmn.h)

	# Install extra files for apache2
	install -d ${D}${sysconfdir}/bash_completion.d \
	           ${D}${sysconfdir}/${DPN}/conf-available \
	           ${D}${localstatedir}/cache/${DPN}/mod_cache_disk \
	           ${D}${sysconfdir}/cron.daily \
	           ${D}${sysconfdir}/default \
	           ${D}${sysconfdir}/logrotate.d
	cp ${S}/debian/bash_completion/apache2 ${D}${sysconfdir}/bash_completion.d/
	cp ${S}/debian/apache2.cron.daily      ${D}${sysconfdir}/cron.daily/apache2
	cp -r ${S}/debian/config-dir/*         ${D}${sysconfdir}/${DPN}/
	sed -e "s#__SERVER_VERSION__#$SERVER_VERSION#" \
	    -e "s#__MODULE_DIR__#$MODULE_DIR#" \
	    -e "s#__API__#$API#" ${S}/debian/a2query.in > ${S}/debian/a2query
	install -m 0755 ${S}/debian/a2enmod \
	                ${S}/debian/apache2ctl \
	                ${S}/debian/a2query ${D}${sbindir}/
	cp ${S}/debian/ask-for-passphrase   ${D}${datadir}/${DPN}/
	cp ${S}/debian/apache2-doc.conf     ${D}${sysconfdir}/${DPN}/conf-available/
	cp ${S}/debian/apache2.default      ${D}${sysconfdir}/default/apache2
	cp ${S}/debian/apache2.logrotate    ${D}${sysconfdir}/logrotate.d/apache2

	# Follow debian/apache2.links
	ln -sf a2enmod    ${D}${sbindir}/a2dismod
	ln -sf a2enmod    ${D}${sbindir}/a2ensite
	ln -sf a2enmod    ${D}${sbindir}/a2dissite
	ln -sf a2enmod    ${D}${sbindir}/a2enconf
	ln -sf a2enmod    ${D}${sbindir}/a2disconf
	ln -sf apache2ctl ${D}${sbindir}/apachectl

	# Follow debian/apache2-dev.links
	ln -sf apxs ${D}${bindir}/apxs2

	# Install extra files for apache2-data
	install ${S}/debian/icons/*.png ${D}${datadir}/${DPN}/icons/
	install ${S}/debian/index.html  ${D}${datadir}/${DPN}/default-site/
	mv ${D}${sbindir}/envvars-std   ${D}${datadir}/${DPN}/build/
	rm -f ${D}${sbindir}/envvars

	# Install extra files for apache2-doc
	install -d ${D}${docdir}/${DPN}-doc/cgi-examples \
	           ${D}${docdir}/${DPN}-doc/examples/${DPN}
	install -m 0644 ${S}/debian/apache2-doc.conf \
		${D}${sysconfdir}/${DPN}/conf-available/
	mv ${D}${datadir}/${DPN}/default-site/htdocs/manual \
		${D}${docdir}/${DPN}-doc
	mv ${D}${libdir}/cgi-bin/printenv \
		${D}${docdir}/${DPN}-doc/cgi-examples/
	mv ${D}${libdir}/cgi-bin/test-cgi \
		${D}${docdir}/${DPN}-doc/cgi-examples/
	mv ${D}${sysconfdir}/${DPN}/extra \
		${D}${docdir}/${DPN}-doc/examples/${DPN}/
	mv ${D}${sysconfdir}/${DPN}/original \
		${D}${docdir}/${DPN}-doc/examples/${DPN}/
	rm -rf ${D}${libdir}/cgi-bin
	rm -rf ${D}${datadir}/${DPN}/default-site/htdocs

	# Install extra files for apache2-suexec-custom
	test -d ${D}${sysconfdir}/${DPN}/suexec/ || \
		mkdir -p ${D}${sysconfdir}/${DPN}/suexec/
	mv ${D}${sbindir}/suexec-custom       ${D}${libexecdir}/
	cp -r ${S}/debian/suexec-config-dir/* ${D}${sysconfdir}/${DPN}/suexec/

	# Install extra files for apache2-suexec-pristine
	mv ${D}${sbindir}/suexec-pristine ${D}${libexecdir}/

	# Install extra files for apache2-utils
	mv ${D}${sbindir}/fcgistarter  ${D}${bindir}/
	mv ${D}${sbindir}/rotatelogs   ${D}${bindir}/
	mv ${D}${sbindir}/htcacheclean ${D}${bindir}/
	mv ${D}${sbindir}/checkgid     ${D}${bindir}/
	mv ${D}${bindir}/httxt2dbm     ${D}${sbindir}/
	install -m 0755 ${S}/support/check_forensic ${D}${sbindir}/
	install -m 0755 ${B}/support/split-logfile  ${D}${sbindir}/
	rm -f ${D}${bindir}/dbmmanage

	# Install initscript
	test -d ${D}${sysconfdir}/init.d || \
		mkdir -p ${D}${sysconfdir}/init.d
	install -m 0755 ${S}/debian/apache2.init ${D}${sysconfdir}/init.d/apache2

	# standard suexec
	chmod 4754 ${D}${libexecdir}/suexec-pristine
	chgrp www-data ${D}${libexecdir}/suexec-pristine
	# configurable suexec
	chmod 4754 ${D}${libexecdir}/suexec-custom
	chgrp www-data ${D}${libexecdir}/suexec-custom
	chown -R www-data:www-data ${D}${localstatedir}/cache/apache2/mod_cache_disk
	chown root:adm ${D}${localstatedir}/log/apache2
	chmod o-rx ${D}${localstatedir}/log/apache2

	# Remove sysroot path
	sed -i -e 's,${STAGING_DIR_HOST},,g' \
	       -e 's,APU_INCLUDEDIR = .*,APU_INCLUDEDIR = ,g' \
	       -e 's,APU_CONFIG = .*,APU_CONFIG = ,g' ${D}${datadir}/apache2/build/config_vars.mk
	sed -i -e 's,${STAGING_DIR_HOST},,g' \
	       -e 's,".*/configure","configure",g' ${D}${datadir}/apache2/build/config.nice
}

# Add files into SSTATE_SCAN_FILES to replace the hardcoded paths
SSTATE_SCAN_FILES += "apxs config_vars.mk config.nice"

# Install cross script
SYSROOT_PREPROCESS_FUNCS += "apache_sysroot_preprocess"
apache_sysroot_preprocess () {
	install -d ${SYSROOT_DESTDIR}${bindir_crossscripts}/
	install -m 755 ${D}${bindir}/apxs ${SYSROOT_DESTDIR}${bindir_crossscripts}/
	sed -i 's!my $installbuilddir = .*!my $installbuilddir = "${STAGING_DIR_HOST}/${datadir}/${BPN}/build";!' ${SYSROOT_DESTDIR}${bindir_crossscripts}/apxs
	sed -i 's!my $libtool = .*!my $libtool = "${STAGING_BINDIR_CROSS}/${TARGET_PREFIX}libtool";!' ${SYSROOT_DESTDIR}${bindir_crossscripts}/apxs

	sed -i 's!^APR_CONFIG = .*!APR_CONFIG = ${STAGING_BINDIR_CROSS}/apr-1-config!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
	sed -i 's!^APU_CONFIG = .*!APU_CONFIG = ${STAGING_BINDIR_CROSS}/apu-1-config!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
	sed -i 's!^includedir = .*!includedir = ${STAGING_INCDIR}/apache2!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
	sed -i 's!^CFLAGS = -I[^ ]*!CFLAGS = -I${STAGING_INCDIR}/openssl!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
	sed -i 's!^EXTRA_LDFLAGS = .*!EXTRA_LDFLAGS = -L${STAGING_LIBDIR}!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
	sed -i 's!^EXTRA_INCLUDES = .*!EXTRA_INCLUDES = -I$(includedir) -I. -I${STAGING_INCDIR}!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
	sed -i 's!--sysroot=[^ ]*!--sysroot=${STAGING_DIR_HOST}!' ${SYSROOT_DESTDIR}${datadir}/${BPN}/build/config_vars.mk
}

CONFFILES_${PN} = " \
    ${sysconfdir}/${DPN}/apache2.conf \
    ${sysconfdir}/${DPN}/magic \
    ${sysconfdir}/${DPN}/mime.types \
    ${sysconfdir}/init.d/apache2 \
"

RDEPENDS_${PN} += "lsb-base perl openssl"

PACKAGES =+ " \
    ${PN}-suexec-custom ${PN}-suexec-pristine \
    ${PN}-bin ${PN}-data \
"

PACKAGE_BEFORE_PN = " \
    ${PN}-utils \
"

FILES_${PN}-bin = "${libdir}/${DPN} ${sbindir}/apache2"
FILES_${PN}-data = " \
    ${datadir}/${DPN}/build/envvars-std \
    ${datadir}/${DPN}/default-site \
    ${datadir}/${DPN}/error \
    ${datadir}/${DPN}/icons \
"
FILES_${PN}-dev += "${bindir}/apxs* ${datadir}/${DPN}/build"
FILES_${PN}-doc += "${sysconfdir}/${DPN}/conf-available/apache2-doc.conf"

FILES_${PN}-suexec-custom = " \
    ${sysconfdir}/${DPN}/suexec \
    ${libdir}/${DPN}/suexec-custom \
"
FILES_${PN}-suexec-pristine = "${libdir}/${DPN}/suexec-pristine"
FILES_${PN}-utils = " \
    ${bindir} \
    ${sbindir}/check_forensic \
    ${sbindir}/httxt2dbm \
    ${sbindir}/split-logfile \
"

FILES_${PN} += "/run/${DPN}"

# Follow debian/control, declare relationships between packages
RDEPENDS_${PN} += "${PN}-bin ${PN}-utils ${PN}-data"
RPROVIDES_${PN} = "httpd httpd-cgi"

RDEPENDS_${PN}-suexec-pristine += "${PN}-bin"
RDEPENDS_${PN}-suexec-custom += "${PN}-bin"

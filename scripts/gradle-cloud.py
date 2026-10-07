#!/usr/bin/env python3
"""Run Gradle with this cloud machine's proxy and trusted CA store."""
import os
import pathlib
import subprocess
import sys
import shlex
import urllib.parse

root = pathlib.Path(__file__).resolve().parent.parent
proxy = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY', ''))
command = [str(root / 'gradlew')] if (root / 'gradlew').exists() else ['/workspace/tools/gradle-9.1.0/bin/gradle']
command += sys.argv[1:]
if proxy.hostname:
    for scheme in ('http', 'https'):
        command += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}']
trust = pathlib.Path('/etc/ssl/certs/java/cacerts')
if trust.exists():
    command += [f'-Djavax.net.ssl.trustStore={trust}']
environment = os.environ.copy()
environment.setdefault('GRADLE_USER_HOME', '/workspace/.gradle')
properties = [arg for arg in command if arg.startswith('-D')]
environment['JAVA_OPTS'] = environment.get('JAVA_OPTS', '') + ' ' + ' '.join(shlex.quote(arg) for arg in properties)
raise SystemExit(subprocess.call(command, cwd=root, env=environment))

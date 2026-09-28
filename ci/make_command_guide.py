"""Build the offline command guide from the user's unchanged 200-row reference.
Each option is [flag, meaning, example, when]. Keep examples read-only unless warned.
"""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'app/src/main/assets'
items=json.loads((root/'commands200.json').read_text())['items']
# Common options. The exact flag spelling is command-specific, not a universal glossary.
raw={
'pwd': [('-P','Resolve symlinks and show physical path','pwd -P','When a directory is reached through a symlink')],
'ls':[('-a','Include hidden dotfiles','ls -a','When config files appear missing'),('-l','Long listing: permissions, owner, size, date','ls -l /etc','When checking ownership'),('-h','Human-readable sizes; combine with -l','ls -lh /var/log','When sizes matter')],
'cd':[('..','Go one directory up','cd ..','When returning to parent'),('-','Switch to previous directory','cd -','When alternating between folders'),('~','Go to your home directory','cd ~','When starting over from home')],
'clear':[], 'history':[('N','Show recent N entries (Bash)','history 20','When recalling recent commands')],
'whoami':[], 'man':[('-k','Search manual page descriptions by keyword','man -k network','When you know a topic but not a command')],
'touch':[('-c','Do not create a file if absent','touch -c app.log','When updating timestamps on existing files only')],
'mkdir':[('-p','Create parents as needed; do not fail if they exist','mkdir -p /tmp/demo/logs','When creating nested folders')],
'cp':[('-r','Copy a directory recursively','cp -r app/ /tmp/app-backup/','When copying a folder'),('-i','Ask before overwriting','cp -i app.conf app.conf.bak','When destination might exist'),('-a','Preserve metadata and symlinks recursively','cp -a app/ /tmp/app-backup/','When making a faithful backup')],
'mv':[('-i','Ask before overwriting destination','mv -i old.conf new.conf','When renaming an important file')],
'rm':[('-i','Ask before removal','rm -i old.txt','When deleting an uncertain file'),('-r','Remove a directory and contents','rm -r /tmp/oldapp','Only after checking the path; cannot undo')],
'rmdir':[], 'stat':[('-c','Print selected metadata format','stat -c %a app.log','When you need numeric permissions')],
'cat':[('-n','Number every output line','cat -n app.conf','When discussing a line number')],
'less':[('-N','Show line numbers','less -N app.log','When locating a reported line')],
'more':[], 'head':[('-n N','Print first N lines','head -n 20 app.log','When checking file start')],
'tail':[('-n N','Print last N lines','tail -n 50 app.log','When reading recent entries'),('-f','Follow appended lines','tail -f app.log','When watching a live log; Ctrl+C stops')],
'wc':[('-l','Count lines','wc -l app.log','When counting records'),('-c','Count bytes','wc -c app.log','When checking byte size')],
'sort':[('-n','Sort numerically','sort -n numbers.txt','When text sorting gives wrong number order'),('-r','Reverse order','sort -r names.txt','When descending order helps')],
'uniq':[('-c','Count repeated adjacent lines','sort users.txt | uniq -c','When tallying values; sort first')],
'diff':[('-u','Unified context format','diff -u old.conf new.conf','When reviewing config changes')],
'grep':[('-i','Ignore case','grep -i error app.log','When ERROR and error both matter'),('-n','Show matching line numbers','grep -n error app.log','When locating a match'),('-v','Exclude matching lines','grep -v debug app.log','When filtering noise'),('-r','Search directories recursively','grep -r DB_HOST /etc/app','When finding text in a tree'),('-E','Extended regular expressions','grep -E "error|failed" app.log','When matching alternatives')],
'find':[('-name PATTERN','Match filename; quote wildcards','find /var -name "*.log"','When locating logs'),('-size +500M','Find files larger than 500 MiB','find /var -type f -size +500M','When chasing disk usage'),('-mtime +30','Modified more than 30 days ago (24h periods)','find /backup -type f -mtime +30','When reviewing old backups')],
'locate':[('-i','Case-insensitive filename lookup','locate -i httpd.conf','When name case is unknown; index may be stale')],
'useradd':[('-m','Create home directory','sudo useradd -m trainee','When creating a login account')],
'adduser':[], 'passwd':[('-l','Lock password authentication (not every login method)','sudo passwd -l trainee','When disabling password login only')],
'usermod':[('-aG GROUP','Append supplementary group; use -a with -G','sudo usermod -aG developers trainee','When granting group membership without removing old groups'),('-s SHELL','Set login shell','sudo usermod -s /bin/bash trainee','When changing a login shell')],
'userdel':[('-r','Also delete home and mail spool','sudo userdel -r trainee','Only after backing up needed user data')],
'groupadd':[], 'groupdel':[], 'groups':[],
'chmod':[('644','Owner read/write; others read','chmod 644 app.conf','When a readable config must not be writable by others'),('+x','Add execute permission','chmod +x backup.sh','When a script needs to run directly'),('-R','Change a tree recursively','chmod -R u+rwX /tmp/demo','Only after reviewing target and desired permissions')],
'chown':[('USER:GROUP','Set owner and group together','sudo chown app:app app.conf','When service account must own a file'),('-R','Recurse into directories','sudo chown -R app:app /srv/app','Only after reviewing path and current ownership')],
'chgrp':[('-R','Change group recursively','sudo chgrp -R developers /srv/project','When a whole tree needs a shared group')],
'umask':[], 'getfacl':[], 'setfacl':[('-m','Modify an ACL entry','setfacl -m u:trainee:r report.txt','When one user needs specific access'),('-x','Remove an ACL entry','setfacl -x u:trainee report.txt','When removing an exception')],
'lsattr':[], 'chattr':[('+i','Make immutable (even root must clear it before editing)','sudo chattr +i important.conf','Only after ensuring updates will not need to write it'),('-i','Remove immutable bit','sudo chattr -i important.conf','When an authorized update is needed')],
'ps':[('aux','BSD-style all-user process detail','ps aux','When checking CPU and memory by process'),('-ef','POSIX-style full process listing','ps -ef','When locating parent/child PIDs')],
'top':[('-p PID','Track one process ID','top -p 1234','When investigating a particular service')],
'htop':[], 'pgrep':[('-a','Show PID and full command line','pgrep -a nginx','When confirming matched processes')],
'pkill':[('-f','Match full command line, not just process name','pkill -f exact-pattern','Only after previewing with pgrep -af; may kill many')],
'kill':[('-TERM','Ask process to shut down gracefully (default)','kill -TERM 1234','First choice to stop a stuck process'),('-9','Force SIGKILL with no cleanup','kill -9 1234','Last resort after graceful stop fails')],
'jobs':[('-l','Include job PIDs','jobs -l','When correlating shell jobs with processes')],
'systemctl':[('status','Inspect unit status and recent messages','systemctl status nginx','When diagnosing a service'),('restart','Stop then start the unit','sudo systemctl restart nginx','After an approved change; causes brief outage'),('reload','Apply config without full restart if supported','sudo systemctl reload nginx','After supported config changes'),('enable','Start unit at boot; does not start now','sudo systemctl enable nginx','When service must survive reboot'),('--type=service','Filter unit list to services','systemctl list-units --type=service','When browsing only services')],
'ip':[('addr show','Show interface IP addresses','ip addr show','When checking address assignment'),('link show','Show interface status','ip link show','When checking link state'),('route','Show routing table','ip route','When diagnosing gateway'),('neigh','Show ARP/neighbor cache','ip neigh','When diagnosing local reachability')],
'ping':[('-c N','Send N probes then stop','ping -c 4 8.8.8.8','When checking reachability without endless output')],
'ss':[('-tuln','TCP/UDP, listening sockets, numeric ports','ss -tuln','When checking what ports are listening'),('-tp','TCP sockets with owning processes (may need sudo)','sudo ss -tp','When linking a connection to a process')],
'hostname':[], 'hostnamectl':[], 'curl':[('-I','Request response headers only','curl -I https://example.com','When checking HTTP status'),('-L','Follow redirects','curl -L https://example.com','When URL redirects'),('-v','Verbose connection diagnostics','curl -v https://example.com','When TLS/HTTP requests fail')],
'nslookup':[('-type=MX','Query mail exchanger records','nslookup -type=MX example.com','When diagnosing email DNS')],
'dig':[('+short','Short DNS answer','dig example.com +short','When only the resolved address matters'),('MX','Ask for MX records','dig example.com MX','When checking mail routing')],
'host':[('-t MX','Select DNS record type','host -t MX example.com','When looking up mail records')],
'traceroute':[('-n','Do not reverse-resolve hops','traceroute -n example.com','When DNS makes traces slow')],
'tracepath':[], 'nmcli':[('device status','Show NetworkManager interfaces','nmcli device status','When diagnosing managed interfaces')],
'nmtui':[], 'ethtool':[('-i','Show NIC driver details','ethtool -i eth0','When checking the network driver')],
'tcpdump':[('-i IFACE','Capture on one interface','sudo tcpdump -i eth0 port 443','When tracing traffic; packet captures may contain private data'),('-c N','Stop after N packets','sudo tcpdump -i eth0 -c 20','When limiting capture size')],
'wget':[('-O FILE','Choose download filename','wget -O file.zip https://example.com/file.zip','When saving under a chosen name')],
'lsblk':[('-f','Show filesystem type, UUID and mount point','lsblk -f','When identifying a disk before mounting')],
'blkid':[], 'fdisk':[('-l','List partitions without changing them','sudo fdisk -l','When inspecting disks; interactive fdisk can alter tables')],
'parted':[('-l','List partition layouts','sudo parted -l','When inspecting partition tables')],
'df':[('-h','Human-readable disk space','df -h','When checking full filesystems'),('-i','Show inode usage','df -i','When writes fail though space remains')],
'du':[('-sh','Summary total in human units','du -sh /var/log','When finding heavy directories'),('-ah','Show all entries in human units','du -ah /var/log','When locating large files')],
'mount':[('-o ro','Mount read-only','sudo mount -o ro /dev/sdb1 /mnt/data','When inspecting an untrusted disk without writing')],
'umount':[], 'pvs':[], 'pvdisplay':[], 'pvcreate':[], 'vgs':[], 'vgdisplay':[], 'vgcreate':[], 'vgextend':[], 'lvs':[], 'lvdisplay':[],
'lvcreate':[('-L SIZE','Set logical volume size','sudo lvcreate -L 5G -n lvapp vgdata','When provisioning within free VG space'),('-n NAME','Name a new logical volume','sudo lvcreate -L 5G -n lvapp vgdata','When creating a named volume')],
'dnf':[('install','Install a package and dependencies','sudo dnf install httpd','When adding a trusted package'),('remove','Remove a package','sudo dnf remove httpd','When retiring software; inspect dependencies'),('search','Find packages by name or description','dnf search nginx','When package name is unknown'),('history','Review package transactions','dnf history','When tracing recent package changes')],
'rpm':[('-qa','Query all installed RPMs','rpm -qa','When auditing installed software'),('-qi','Query installed package metadata','rpm -qi httpd','When checking version/vendor'),('-ql','List files installed by a package','rpm -ql httpd','When finding a config'),('-qf','Find owner of installed file','rpm -qf /usr/sbin/httpd','When identifying which RPM installed a binary')],
'journalctl':[('-u UNIT','Show logs for one systemd unit','journalctl -u nginx','When troubleshooting a service'),('-f','Follow new log entries','journalctl -f','When watching an active issue'),('-b','Show current boot only','journalctl -b','When checking startup'),('--since TIME','Filter by start time','journalctl --since "1 hour ago"','When narrowing recent events')],
'dmesg':[('-T','Display human-readable timestamps (approximate)','dmesg -T | tail','When checking recent kernel messages')],
'logger':[], 'last':[], 'lastb':[],
'ssh':[('-p PORT','Connect to non-default SSH port','ssh -p 2222 user@server','When server uses a custom port'),('-v','Show connection diagnostics','ssh -v user@server','When login fails'),('-i KEY','Choose an identity file','ssh -i ~/.ssh/id_ed25519 user@server','When selecting a key')],
'scp':[('-r','Copy a directory recursively','scp -r app/ user@server:/opt/','When transferring a folder'),('-P PORT','Use remote SSH port (uppercase P)','scp -P 2222 app.conf user@server:/tmp/','When SSH has a custom port')],
'sftp':[('-P PORT','Connect to custom SSH port','sftp -P 2222 user@server','When using non-default SSH port')],
'ssh-keygen':[('-t TYPE','Select key algorithm','ssh-keygen -t ed25519','When creating a modern SSH key')],
'ssh-copy-id':[('-i KEY','Choose public key to install','ssh-copy-id -i ~/.ssh/id_ed25519.pub user@server','When enrolling the chosen key')],
'ssh-keyscan':[('-p PORT','Fetch host key from custom port','ssh-keyscan -p 2222 server','When gathering a key; verify fingerprint independently')],
'sshd':[('-t','Test SSH daemon configuration syntax','sudo sshd -t','Before restarting SSH after edits')],
'tar':[('-cf','Create archive file','tar -cf backup.tar /data','When bundling files; tar alone does not compress'),('-xf','Extract archive','tar -xf backup.tar -C /tmp/restore','When restoring to a checked destination'),('-tf','List contents before extraction','tar -tf backup.tar','When checking unknown archive paths'),('-czf','Create gzip-compressed tar','tar -czf logs.tar.gz /var/log/app','When archiving and compressing')],
'gzip':[('-k','Keep original file','gzip -k app.log','When compression must not remove original')],
'gunzip':[('-k','Keep compressed source','gunzip -k app.log.gz','When retaining archive')],
'zip':[('-r','Include directories recursively','zip -r backup.zip app/','When archiving a folder')],
'unzip':[('-l','List files before extraction','unzip -l backup.zip','When checking contents safely'),('-d DIR','Extract to chosen directory','unzip backup.zip -d /tmp/restore','When controlling output location')],
'zcat':[],
'crontab':[('-e','Edit current user crontab','crontab -e','When scheduling recurring jobs'),('-l','List current user crontab','crontab -l','When auditing schedules'),('-r','Remove entire current user crontab','crontab -r','Danger: backs up crontab -l first; removes all entries')],
'at':[], 'atq':[], 'atrm':[], 'watch':[('-n SEC','Refresh interval in seconds','watch -n 2 df -h','When watching disk space change')],
'sleep':[], 'timeout':[('10s','Limit runtime to 10 seconds','timeout 10s ping server','When a command may hang')],
'echo':[('-n','Do not append newline','echo -n ready','When composing output on one line')],
'printf':[], 'env':[('-i','Run with an empty environment','env -i /usr/bin/printenv','When testing environment dependencies')],
'printenv':[], 'export':[], 'source':[], 'alias':[], 'unalias':[], 'awk':[('-F SEP','Specify input field delimiter','awk -F: "{print \\$1}" /etc/passwd','When parsing colon-separated records')],
'sed':[('-n','Suppress default output; print only selected lines','sed -n "1,10p" file.txt','When extracting a line range'),('-i','Edit file in place','sed -i.bak "s/old/new/g" file.txt','Only after checking replacement; .bak keeps backup')],
'uname':[('-a','Print all available system info','uname -a','When checking kernel and architecture'),('-r','Print kernel release','uname -r','When checking module compatibility')],
'lscpu':[], 'lsmem':[], 'lsusb':[], 'lspci':[], 'vmstat':[('1 5','Sample every 1 second, 5 times','vmstat 1 5','When watching CPU, memory and I/O')],
'iostat':[('-xz','Extended device stats; omit inactive devices','iostat -xz 1 3','When investigating disk latency')],
'sar':[('-u','Report CPU utilization','sar -u 1 5','When observing CPU over time')],
'sudo':[('-u USER','Run command as specified user','sudo -u appuser whoami','When testing service account access')],
'su':[('-','Start a login shell for target user','su - appuser','When environment should match normal login')],
'visudo':[('-c','Check sudoers syntax','sudo visudo -c','Before deploying sudoers changes')],
'getenforce':[], 'sestatus':[],
'firewall-cmd':[('--state','Check if firewalld is running','sudo firewall-cmd --state','When rules seem inactive'),('--list-all','Show active zone settings','sudo firewall-cmd --list-all','When auditing open services'),('--add-service=NAME','Allow named service','sudo firewall-cmd --add-service=http --permanent','Only after change approval; permanent needs reload'),('--reload','Apply permanent config to runtime','sudo firewall-cmd --reload','After verified permanent changes')],
'openssl':[('s_client','Connect and inspect TLS server','openssl s_client -connect example.com:443 -servername example.com','When debugging TLS certificates')],
}
notes={
'rm':'Destructive: verify the exact path with ls before deleting. Never paste an unknown rm -rf command.',
'pvcreate':'Destructive: initializes PV metadata on a disk. Confirm disk identity, backups and change approval before use.',
'vgcreate':'Creates a volume group on an initialized PV. Confirm disk and existing data before use.',
'vgextend':'Adds a PV to a VG. Confirm disk identity and free capacity first.',
'lvcreate':'Allocates space from VG. Check free extents and filesystem plan first.',
'mount':'Mount point contents are hidden while mounted; verify device and mount point first.',
'umount':'Unmount only after users/processes release the filesystem.',
'userdel':'Removing a user can affect files and service ownership; back up first.',
'pkill':'Matches all named processes. Preview with pgrep before sending a signal.',
'kill':'Prefer graceful SIGTERM; SIGKILL prevents cleanup.',
'chattr':'Immutable attribute blocks edits, including by administrators until cleared.',
'crontab':'Use crontab -l to back up before -r, which deletes the whole crontab.',
'fdisk':'Listing with -l is read-only; partition changes can destroy data.',
'parted':'Listing with -l is read-only; interactive edits can destroy data.',
'ssh-keyscan':'A fetched key is not authenticated. Check its fingerprint over a trusted channel.',
'firewall-cmd':'A firewall change can cut off remote access. Verify zone and recovery path first.',
}
# For rows that already contain a specific flag, put its explanation first if present.
def command_key(s):
    if s.startswith('cat /etc/os-release'):return 'os-release'
    return s.split()[0]
raw['os-release']=[]
raw['sleep']=[]
for i in items:
    k=command_key(i['command']); assert k in raw,(i['n'],k)
    flags=raw[k]
    cmd=i['command']
    matched=[f for f in flags if f[0] in cmd.split()[1:] or (k in ('tar','rpm','ss') and f[0] in cmd)]
    opts=matched+[f for f in flags if f not in matched]
    # A row is one command/example/use. Expanded options are command-specific, even if the source had no flags.
    i['options']=[{'option':f,'meaning':m,'example':e,'when':w} for f,m,e,w in opts]
    if not flags:
        i['no_flags']='This example uses an argument or subcommand rather than a short flag. The value after the command selects its target or action; run man '+k+' or '+k+' --help on your own distribution for more options.'
    i['tip']=notes.get(k,'')
# Correct a legacy example that would not work if shell-quoted literally in app text.
for i in items:
    if i['n']==179:
        i['options'][0]['example']="awk -F: '{print $1}' /etc/passwd"
(root/'command_guide.json').write_text(json.dumps({'topics':list(dict.fromkeys(i['topic'] for i in items)),'items':items},ensure_ascii=False,indent=2)+'\n')
print(len(items),'items',len(raw),'base commands',sum(bool(x['options']) for x in items),'with option guidance')

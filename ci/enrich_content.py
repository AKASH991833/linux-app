"""Curated primary references and teaching examples for the offline guide.
Run AFTER make_command_guide.py; does not alter Akash's original PDF rows.
"""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]/'app/src/main/assets'
p=r/'command_guide.json'; guide=json.loads(p.read_text())
# Publisher/command-family docs are not certification that every specific sentence was verified.
docs={
'bash':('Bash manual (man7 mirror)','https://man7.org/linux/man-pages/man1/bash.1.html'),
'core':('GNU coreutils manual page (man7 mirror)','https://man7.org/linux/man-pages/man1/{}.1.html'),
'find':('findutils manual page (man7 mirror)','https://man7.org/linux/man-pages/man1/find.1.html'),
'grep':('GNU grep manual page (man7 mirror)','https://man7.org/linux/man-pages/man1/grep.1.html'),
'ip':('iproute2 manual page (man7 mirror)','https://man7.org/linux/man-pages/man8/ip.8.html'),
'systemctl':('systemd systemctl manual','https://freedesktop.org/software/systemd/man/latest/systemctl.html'),
'journalctl':('systemd journalctl manual','https://freedesktop.org/software/systemd/man/latest/journalctl.html'),
'lvm':('Red Hat LVM guide','https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html-single/configuring_and_managing_logical_volumes/index'),
'dnf':('Red Hat DNF commands','https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/managing_software_with_the_dnf_tool/assembly_yum-commands-list_managing-software-with-the-dnf-tool'),
'firewall':('Red Hat firewalld guide','https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/configuring_firewalls_and_packet_filters/using-and-configuring-firewalld_firewall-packet-filters'),
'ssh':('OpenSSH manuals','https://www.openssh.org/manual.html'),
}
core={'ls','pwd','touch','mkdir','cp','mv','rm','rmdir','stat','cat','head','tail','wc','sort','uniq','dd','chmod','chown','chgrp','whoami','date','du','df','env','printenv','sleep','timeout','uname','tee'}
bash={'cd','history','umask','jobs','kill','echo','printf','export','source','alias','unalias','su','type'}
iproute={'ip','ss'}
lvm={'pvs','pvdisplay','pvcreate','vgs','vgdisplay','vgcreate','vgextend','lvs','lvdisplay','lvcreate'}
ssh={'ssh','scp','sftp','ssh-keygen','ssh-copy-id','ssh-keyscan','sshd'}
for i in guide['items']:
 k=i['command'].split()[0]
 if k in core: title,url=docs['core'];url=url.format(k)
 elif k in bash:title,url=docs['bash']
 elif k in iproute:title,url=docs['ip']
 elif k in lvm:title,url=docs['lvm']
 elif k in ssh:title,url=docs['ssh']
 elif k in ('dnf','rpm'):title,url=docs['dnf']
 elif k=='firewall-cmd':title,url=docs['firewall']
 elif k in docs:title,url=docs[k]
 elif k in {'grep','find'}:title,url=docs[k]
 else: title,url=('Linux command manual index (man7)','https://man7.org/linux/man-pages/dir_section_1.html')
 i['sourceTitle']=title;i['sourceUrl']=url
 if url.endswith('/dir_section_1.html'):
  i['sourceTitle']='Linux manual index (find this command)'
  import urllib.request
  candidate='https://man7.org/linux/man-pages/man'+('8' if k in {'useradd','usermod','userdel','groupadd','groupdel','fdisk','mount','umount','mkfs','blkid','tcpdump','visudo','sshd'} else '1')+'/'+k+'.'+('8' if k in {'useradd','usermod','userdel','groupadd','groupdel','fdisk','mount','umount','mkfs','blkid','tcpdump','visudo','sshd'} else '1')+'.html'
  try:
   if urllib.request.urlopen(urllib.request.Request(candidate,method='HEAD'),timeout=2).status==200:i['sourceUrl']=candidate
  except Exception:pass
 # Fix misleading 'no flags' boilerplate: don't suggest --help on Bash builtins or data-only path entries.
 if not i['options']:
  i['no_flags']='This example uses a command, argument, or subcommand rather than an everyday short flag. See the linked manual for supported options on your distribution.'
 # User-facing descriptive caution distinguishing help flags across commands.
 if k=='ls':i['tip']='Combine ls -lah for long details, hidden files and readable sizes. Flags are command-specific: -h is not universal help.'
 if k=='pvcreate':i['tip']='Destructive: writes LVM metadata to the named device. Confirm the exact disk and data backups before running.'
 if k=='crontab' and i['command']=='crontab -r':
  i['tip']='Danger: removes every entry in the current user crontab, not just one task. Back up crontab -l first.'
 if k=='du' and '-ah' in i['command']:
  i['tip']='This scans a whole tree and can be slow; narrow the path first. Sort -h sorts human-readable sizes.'
# Correct two option explanations against tool manuals.
for i in guide['items']:
 for o in i['options']:
  if i['command'].startswith('ls') and o['option']=='-h':o['meaning']='Human-readable sizes in detailed listings; use with -l (ls -lh)'
  if i['command'].startswith('find') and o['option']=='-size +500M':o['meaning']='File size greater than 500 units of 1,048,576 bytes (GNU find rounds up to whole units)'
p.write_text(json.dumps(guide,ensure_ascii=False,indent=2)+'\n')
D=r/'definitions.json'; data=json.loads(D.read_text())
examples={
'Absolute vs relative path':'From /home/akash, notes.txt means /home/akash/notes.txt; /etc/hosts is absolute no matter where you are.',
'Bash':'In Bash, pwd prints your directory; for a reusable task, put commands in a .sh script.',
'Cron':'A cron entry can run a backup script at 02:00; check its environment and log errors.',
'Daemon':'sshd runs in the background and handles incoming SSH connections.',
'Distribution (distro)':'Ubuntu and RHEL combine the Linux kernel with different packages and update tools.',
'DNS':'A browser looks up the address for example.com before making a connection.',
'Environment variable':'PATH lists directories the shell searches when you type a command name.',
'Exit code':'After a command, echo $? shows 0 for success or a nonzero code for an error.',
'File permissions':'-rw-r----- means the owner can read/write, the group can read, others have no access.',
'Filesystem hierarchy':'Configuration commonly lives under /etc; logs are commonly under /var/log.',
'Glob (wildcard)':'ls *.log asks the shell to expand *.log to matching log filenames.',
'Hard link':'Two names can point to the same inode on one filesystem; deleting one name leaves the other.',
'Inode':'ls -i report.txt prints the inode number for the file on that filesystem.',
'Kernel':'The kernel schedules processes and mediates access to memory, disks and network devices.',
'Mount':'mount /dev/sdb1 /mnt/data attaches that filesystem at /mnt/data; verify the device first.',
'Package manager':'On RHEL use dnf info nginx to inspect a package before installing it.',
'PATH':'command -v python3 shows the executable found through your PATH.',
'PID (Process ID)':'pgrep -a nginx prints matching process IDs and command lines.',
'Pipe (|)':'journalctl -u sshd | grep Failed filters a service log; check exact unit name first.',
'Port':'SSH usually listens on TCP port 22, though administrators can choose a different port.',
'Process':'ps -ef lists processes; each running instance has its own PID.',
'Redirection (> and >>)':'echo ready > status.txt replaces the file; echo next >> status.txt appends.',
'Regular expression':'grep -E "error|failed" app.log matches either word in the log.',
'Root':'/ is the root directory; the root user is a separate concept with administrator privileges.',
'Shebang (#!)':'#!/usr/bin/env bash on line 1 selects Bash when you run an executable script.',
'Shell':'In Bash, cd /etc changes the shell\'s working directory.',
'Signal':'kill -TERM 1234 requests graceful shutdown; -KILL is a last resort.',
'SSH key':'An SSH public key can be installed on a server; keep its private counterpart secret.',
'stderr (standard error)':'command 2>errors.log redirects standard error to a file.',
'stdin (standard input)':'sort < names.txt reads names from the file as standard input.',
'stdout (standard output)':'pwd > location.txt writes normal output to a file.',
'sudo':'sudo systemctl status nginx runs the check with elevated privileges if policy allows.',
'Symbolic link (symlink)':'ln -s /srv/app/current app-link makes app-link point to the target path.',
'System call':'A program uses open() to ask the kernel to open a file.',
'systemd':'systemctl status nginx shows the status of an nginx service managed by systemd.',
'Terminal':'A terminal app shows the shell prompt and displays a command\'s output.',
'TTY':'The tty command reports which terminal device is attached to your session.',
'umask':'umask 027 removes group write and all other-user permissions from new file modes.',
'Working directory':'pwd prints your working directory; relative paths are resolved from there.',
}
assert len(examples)==39
for g in data['groups']:
 for i in g['items']:
  if i['category']=='Linux':
   i['example']=examples[i['term']]
   # Add independent primary references for selected notions; retain original source separately.
   if i['term'] in ('Bash','Environment variable','Exit code','Glob (wildcard)','PATH','Pipe (|)','Redirection (> and >>)','Shebang (#!)','Shell','stderr (standard error)','stdin (standard input)','stdout (standard output)','umask','Working directory'):
    i['additionalSourceTitle'],i['additionalSourceUrl']=docs['bash']
   elif i['term']=='systemd': i['additionalSourceTitle'],i['additionalSourceUrl']=docs['systemctl']
fix={
'Bash':('Bash is a command-line shell and scripting language. It runs commands interactively and supports variables, loops, functions and job control. Your default shell depends on your account and distribution. A script can request Bash with #!/usr/bin/env bash.',docs['bash']),
'Cron':('Cron is a scheduler for recurring commands. A user crontab commonly has five time fields followed by a command, but system crontabs can also include a user field. Cron jobs run without an interactive terminal and may have a different environment than your shell, so specify paths and capture errors.',('crontab manual','https://man7.org/linux/man-pages/man5/crontab.5.html')),
'Absolute vs relative path':('An absolute path begins at / and identifies a location from the filesystem root. A relative path is resolved from the current working directory. In a typical shell, ~ expands to the home directory, while .. means the parent directory. Scripts should use paths that fit their runtime directory; do not assume a cron job starts in the project folder.',docs['bash']),
'Kernel':('The Linux kernel is the core of the operating system. It manages processes, memory, filesystems and devices and provides system calls for programs to request these services. A Linux distribution combines the kernel with system tools, libraries and applications.',('Linux man-pages overview','https://man7.org/linux/man-pages/man2/intro.2.html')),
'File permissions':('Linux permission bits grant read, write and execute access separately to the owner, group and others. For directories, execute permits traversal. ACLs and other security controls can add rules, so ls -l alone may not show every access decision.',('chmod manual','https://man7.org/linux/man-pages/man1/chmod.1.html')),
'Filesystem hierarchy':('Linux uses one directory tree rooted at /. Directories such as /etc (system configuration), /home (user homes) and /var/log (often logs) follow common conventions, but layout varies by distribution and service.',('filesystem hierarchy manual','https://man7.org/linux/man-pages/man7/hier.7.html')),
'Root':('Root can mean the top directory / or the superuser account (UID 0). These are different: cd / changes location, while a root shell has broad privileges. Use elevated access only when needed.',docs['bash']),
'umask':('A process umask removes permission bits from the requested mode when new files and directories are created. For example, umask 027 typically yields 640 for ordinary files created from 666 and 750 for directories created from 777. Programs can request more restrictive modes.',('umask manual','https://man7.org/linux/man-pages/man2/umask.2.html')),
}
for group in data['groups']:
 for item in group['items']:
  if item['category']=='Linux' and item['term'] in fix:
   item['definition'],(item['sourceTitle'],item['sourceUrl'])=fix[item['term']]
D.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
print('sources added:',len(guide['items']),'Linux definition practice examples:',len(examples))

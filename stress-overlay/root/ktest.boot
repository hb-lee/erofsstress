#!/bin/bash -e

set +x

printf "Stressing EROFS in QEMU..."

mount -t tmpfs tmpfs /mnt
mkdir -p /mnt/{log,golden,testA,testB}
mount -t ext4 -o noload /dev/vda /mnt/log
ls /dev/vd*
#ls /dev/sd*

echo 4 > /proc/sys/vm/drop_caches
TIMEOUT=3600
WORKERS=7
SEED=123
SHARE=0
for x in `cat /proc/cmdline`; do
	case $x in
		qemuktest.timeout=*)
			TIMEOUT="${x//qemuktest.timeout=}"
		;;
		qemuktest.seed=*)
			SEED="${x//qemuktest.seed=}"
		;;
		qemuktest.share=*)
		    SHARE="${x//qemuktest.share=}"
		;;
	esac
done

echo 100000000 > /proc/sys/fs/nr_open
ulimit -n 100000000

set -e

if [ $SHARE -eq 0 ]; then
  mount -t erofs -oro /dev/vdb /mnt/golden
  mount -t erofs -oro /dev/vdc /mnt/testA
  mount | grep erofs
  timeout -k30 $TIMEOUT stdbuf -o0 -e0 /root/stress -p$WORKERS -s$SEED -l0 -d/mnt/log/baddump /mnt/testA /mnt/golden || [ $? -ne 124 ] && { sync; exit; }
else
  mount -t erofs -oro,inode_share,domain_id=test /dev/vdb /mnt/golden
  mount -t erofs -oro,inode_share,domain_id=test /dev/vdc /mnt/testA
  mount -t erofs -oro,inode_share,domain_id=test /dev/vdd /mnt/testB
  mount | grep erofs
  timeout -k30 $TIMEOUT stdbuf -o0 -e0 /root/stress -p$WORKERS -s$SEED -l0 -d/mnt/log/baddump /mnt/testA /mnt/golden || [ $? -ne 124 ] && { sync; exit; } &
  pidA=$!
  sleep 1
  timeout -k30 $TIMEOUT stdbuf -o0 -e0 /root/stress -p$WORKERS -s$SEED -l0 -d/mnt/log/baddump /mnt/testB /mnt/golden || [ $? -ne 124 ] && { sync; exit; }
  wait $pidA
fi

echo 0 > /mnt/log/exitstatus
sync
umount /mnt/log

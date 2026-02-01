#!/bin/bash -e

set +x

printf "Stressing EROFS in QEMU..."

mount -t tmpfs tmpfs /mnt
mkdir -p /mnt/{log,golden,testA,testB}
mount -t ext4 -o noload /dev/vda /mnt/log
ls /dev/vd*
#ls /dev/sd*
mount -t erofs -oro /dev/vdb /mnt/golden
mount -t erofs -oro /dev/vdc /mnt/testA

echo 4 > /proc/sys/vm/drop_caches
TIMEOUT=3600
WORKERS=7
SEED=123
for x in `cat /proc/cmdline`; do
	case $x in
		qemuktest.timeout=*)
			TIMEOUT="${x//qemuktest.timeout=}"
		;;
		qemuktest.seed=*)
			SEED="${x//qemuktest.seed=}"
		;;
	esac
done

echo 100000000 > /proc/sys/fs/nr_open
ulimit -n 100000000

set -e

run_test() {
    test="$1"
	golden="$2"
    timeout -k30 $TIMEOUT stdbuf -o0 -e0 /root/stress -p$WORKERS -s$SEED -l0 -d/mnt/log/baddump $test $golden
    exit_code=$?
    [ $exit_code -ne 124 ] && { sync; exit; }
}

run_test /mnt/testA /mnt/golden
umount /mnt/golden
umount /mnt/testA

mount -t erofs -oro,inode_share,domain_id=test /dev/vdb /mnt/golden
mount -t erofs -oro,inode_share,domain_id=test /dev/vdc /mnt/testA
mount -t erofs -oro,inode_share,domain_id=test /dev/vdd /mnt/testB
run_test /mnt/testA /mnt/golden &
run_test /mnt/testB /mnt/golden &

echo 0 > /mnt/log/exitstatus
sync
umount /mnt/log

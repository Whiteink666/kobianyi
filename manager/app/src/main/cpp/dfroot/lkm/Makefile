KDIR := $(KDIR)
MDIR := $(realpath $(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
KMI := $(notdir $(patsubst %/,%,$(KDIR)))

# 6.18 的 Kbuild 需要显式的输出目录，其余 KMI 用 src= 就行
ifeq ($(KMI),android17-6.18)
KBUILD_ARGS := M=$(MDIR) MO=$(MDIR)
else
KBUILD_ARGS := M=$(MDIR) src=$(MDIR)
endif

obj-m += dfroot.o

# Size diet: this ko is written through the exploit page by page,
# so every byte counts. -Os shrinks code; dropping unwind tables kills
# .eh_frame (unused for modules).
CFLAGS_dfroot.o := -Os -fno-asynchronous-unwind-tables -fno-unwind-tables

# Package that owns the staged ksud/bootstrap binaries.
DF_PACKAGE ?= df.root
ccflags-y += -DDF_PACKAGE=\"$(DF_PACKAGE)\"

all:
	$(MAKE) -C $(KDIR) $(KBUILD_ARGS) ARCH=arm64 LLVM=1 modules

clean:
	$(MAKE) -C $(KDIR) $(KBUILD_ARGS) ARCH=arm64 LLVM=1 clean
	rm -f dfroot.lds

.PHONY: all clean

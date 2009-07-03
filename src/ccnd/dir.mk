# makefile for src/ccnd directory

LDLIBS = -L$(CCNLIBDIR) $(MORE_LDLIBS) -lccn
CCNLIBDIR = ../lib

INSTALLED_PROGRAMS = ccnd ccndsmoketest
PROGRAMS = $(INSTALLED_PROGRAMS) smoketestccnd
DEBRIS = anything.ccnb contenthash.ccnb contentmishash.ccnb

BROKEN_PROGRAMS = 
CSRC = ccnd.c ccnd_stats.c ccnd_internal_client.c ccndsmoketest.c
HSRC = ccnd_private.h
SCRIPTSRC = testbasics fortunes.ccnb contenthash.ref anything.ref
 
default: $(PROGRAMS)

all: default $(BROKEN_PROGRAMS)

$(PROGRAMS): $(CCNLIBDIR)/libccn.a

CCND_OBJ = ccnd.o ccnd_stats.o ccnd_internal_client.o
ccnd: $(CCND_OBJ) 
	$(CC) $(CFLAGS) -o $@ $(CCND_OBJ) $(LDLIBS) $(OPENSSL_LIBS) -lcrypto

# XXX - smoketestccnd is built for compatibility.
smoketestccnd: ccndsmoketest
	cp -p ccndsmoketest smoketestccnd

ccndsmoketest: ccndsmoketest.o
	$(CC) $(CFLAGS) -o $@ ccndsmoketest.o $(LDLIBS)

clean:
	rm -f *.o *.a $(PROGRAMS) $(BROKEN_PROGRAMS) depend
	rm -rf *.dSYM $(DEBRIS)

check test: ccnd ccndsmoketest $(SCRIPTSRC)
	./testbasics
	: ---------------------- :
	:  ccnd unit tests pass  :
	: ---------------------- :

###############################
# Dependencies below here are checked by depend target
# but must be updated manually.
###############################
ccnd.o: ccnd.c ../include/ccn/ccn.h ../include/ccn/coding.h \
  ../include/ccn/charbuf.h ../include/ccn/indexbuf.h \
  ../include/ccn/ccnd.h ../include/ccn/bloom.h ../include/ccn/hashtb.h \
  ../include/ccn/schedule.h ../include/ccn/uri.h ccnd_private.h \
  ../include/ccn/ccn_private.h
ccnd_stats.o: ccnd_stats.c ../include/ccn/ccn.h ../include/ccn/coding.h \
  ../include/ccn/charbuf.h ../include/ccn/indexbuf.h \
  ../include/ccn/ccnd.h ../include/ccn/schedule.h ../include/ccn/hashtb.h \
  ../include/ccn/uri.h ccnd_private.h ../include/ccn/ccn_private.h
ccnd_internal_client.o: ccnd_internal_client.c ../include/ccn/ccn.h \
  ../include/ccn/coding.h ../include/ccn/charbuf.h \
  ../include/ccn/indexbuf.h ../include/ccn/ccn_private.h \
  ../include/ccn/keystore.h ../include/ccn/schedule.h \
  ../include/ccn/signing.h ../include/ccn/uri.h ccnd_private.h
ccndsmoketest.o: ccndsmoketest.c ../include/ccn/ccnd.h

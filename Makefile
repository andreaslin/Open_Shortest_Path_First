JFLAGS = -g
JC = javac
ROUTER = router
1 = {1}
2 = {2}
3 = {3}
4 = {4}

.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	packet.java \
	router.java

default: classes
	echo '#!/bin/bash\njava router $${1} $${2} $${3} $${4}' | cat > router
	chmod a+x router

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
	$(RM) router
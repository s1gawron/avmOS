CC = gcc
CFLAGS = -Wall -Wextra -std=c11
SRCS = src/main.c src/vm_instance.c
BINDIR = bin
INCLUDEFLAG = -I ./include

avmOS: 
	mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(INCLUDEFLAG) $(SRCS) -o $(BINDIR)/avmOS

clean:
	rm -rf $(BINDIR)
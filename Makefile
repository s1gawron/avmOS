CC = gcc
CFLAGS = -Wall -Wextra -std=c11
SRCDIR = src
BINDIR = bin
INCLUDEFLAG = -I include

avmOS: 
	mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(INCLUDEFLAG) $(SRCDIR)/main.c -o $(BINDIR)/avmOS

clean:
	rm -rf $(BINDIR)
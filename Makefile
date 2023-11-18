CC = gcc
CFLAGS = -Wall -Wextra -std=c11
SRCDIR = src
BINDIR = bin

.PHONY: avmOS clean

avmOS: 
	mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(SRCDIR)/main.c -o $(BINDIR)/avmOS

clean:
	rm -rf $(BINDIR)
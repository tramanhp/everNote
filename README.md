# evernote

## Pre-requisites
1. Java SE Development Kit 5 or above
2. An Evernote Developer Token (retreive a token at https://www.evernote.com/api/DeveloperToken.action)

## Getting Started

1. Download the zip package and unpack the files into a directory
2. Open `everNote_user.java` and paste your Developer Token in `everNote.token_g`. For UNIX systems, you can open a terminal, `cd` to the directiory with `everNote.java`, then run `export everNote_APIKey=[API Key]`
3. Compile with `javac -cp evernote_api.jar:. everNote.java` and run with `java -cp evernote_api.jar:. everNote`

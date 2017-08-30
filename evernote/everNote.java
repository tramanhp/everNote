import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.edam.type.Data;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.ResourceAttributes;
import com.evernote.edam.type.Tag;
import com.evernote.thrift.transport.TTransportException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.security.MessageDigest;
import java.util.Iterator;
import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;

public class everNote {
	static String procCode_g;
	static String token_g;

	static UserStoreClient userStore_g;
	static NoteStoreClient noteStore_g;
	static String newNoteGuid_g;
	static int errorCnt_g;

	static SimpleDateFormat formatter_i = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss");

	/**
	* Update the tags assigned to a note. This method demonstrates how only
	* modified fields need to be sent in calls to updateNote.
	*/
	private void updateNoteTag () throws Exception {
		//When updating a note, it is only necessary to send Evernote the
		//fields that have changed. For example, if the Note that you
		//send via updateNote does not have the resources field set, the
		//Evernote server will not change the note's existing resources.
		//If you wanted to remove all resources from a note, you would
		//set the resources field to a new List<Resource> that is empty.

		//If you are only changing attributes such as the note's title or tags,
		//you can save time and bandwidth by omitting the note content and
		//resources.

		//In this sample code, we fetch the note that we created earlier,
		//including
		//the full note content and all resources. A real application might
		//do something with the note, then update a note attribute such as a
		//tag.
		Note note = noteStore_g.getNote (newNoteGuid_g, true, true, false, false);

		//Do something with the note contents or resources...

		//Now, update the note. Because we're not changing them, we unset
		//the content and resources. All we want to change is the tags.
		note.unsetContent ();
		note.unsetResources ();

		//We want to apply the tag "TestTag"
		note.addToTagNames ("TestTag");

		//Now update the note. Because we haven't set the content or resources,
		//they won't be changed.
		noteStore_g.updateNote (note);
		System.out.println ("Successfully added tag to existing note");

		//To prove that we didn't destroy the note, let's fetch it again and
		//verify that it still has 1 resource.
		note = noteStore_g.getNote (newNoteGuid_g, false, false, false, false);
		System.out.println ("After update, note has " + note.getResourcesSize () + " resource(s)");
		System.out.println ("After update, note tags are: ");
		for (String tagGuid : note.getTagGuids ()) {
		  Tag tag = noteStore_g.getTag (tagGuid);
		  System.out.println ("* " + tag.getName ());
		}

		System.out.println ();
	}


	/**
	* Helper method to read the contents of a file on disk and create a new Data
	* object.
	*/
	private static Data readFileAsData (String fileName) throws Exception {
		FileInputStream in = new FileInputStream (fileName);
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream ();
		byte [] block = new byte [10240];
		int len;
		while ((len = in.read(block)) >= 0) {
		  byteOut.write (block, 0, len);
		}

		in.close ();
		byte [] body = byteOut.toByteArray ();

		//Create a new Data object to contain the file contents
		Data data = new Data ();
		data.setSize (body.length);
		data.setBodyHash (MessageDigest.getInstance ("MD5").digest (body));
		data.setBody (body);

		return data;
	}


	/**
	* Helper method to convert a byte array to a hexadecimal string.
	*/
	public static String bytesToHex (byte [] bytes) {
		StringBuilder sb = new StringBuilder ();
		for (byte hashByte : bytes) {
			int intVal = 0xff & hashByte;
			if (intVal < 0x10)
				sb.append ('0');

			sb.append (Integer.toHexString (intVal));
		}

		return sb.toString ();
	}


	/***
	Intialize UserStore and NoteStore clients. During this step, we
	authenticate with the Evernote Web service.
	*/
	public everNote (String token) throws Exception {
//		System.out.println (token);

//		Set up the UserStore client and check that we can speak to the server
//  	EvernoteAuth evernoteAuth = new EvernoteAuth (EvernoteService.SANDBOX, token);
		EvernoteAuth evernoteAuth = new EvernoteAuth (EvernoteService.PRODUCTION, token);
		ClientFactory factory = new ClientFactory (evernoteAuth);
		userStore_g = factory.createUserStoreClient ();

		boolean versionOk = userStore_g.checkVersion ("Evernote EDAMDemo (Java)", com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR, com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);

		if (versionOk == false) {
			System.err.println ("Incompatible Evernote client protocol version");
			System.exit (1);
		}

		//Set up the NoteStore client
		noteStore_g = factory.createNoteStoreClient ();
	}


	static void doDelete (String guid) throws Exception {
		noteStore_g.deleteNote (guid);
		System.out.println ("Note deleted");
	}


	static void doSelect (String guid) throws Exception {
		System.out.println (noteStore_g.getNoteContent (guid));
	}


	static void doGrep (String type, String query) throws Exception {
		//Searches are formatted according to the Evernote search grammar.
		//Learn more at
		//http://dev.evernote.com/documentation/cloud/chapters/Searching_notes.php

		if (type.equals ("title"))
			query = "intitle:" + query;
		else if (type.equals ("tag"))
			query = "tag:" + query;

		NoteFilter filter = new NoteFilter ();
		filter.setWords (query);
		filter.setOrder (NoteSortOrder.UPDATED.getValue ());
		filter.setAscending (false);

		NoteList notes = noteStore_g.findNotes (filter, 0, 10000); //Display the first 10000 notes
		System.out.printf ("Found %d\n\n", notes.getTotalNotes ());

		Iterator<Note> iter = notes.getNotesIterator ();
		while (iter.hasNext ()) {
			Note note = iter.next ();
			System.out.printf ("Guid: %s\n", note.getGuid ());
			System.out.printf ("Title: %s\n", note.getTitle ());
			System.out.printf ("Created: %s\n", formatter_i.format (new Date (note.getCreated ())));
			System.out.printf ("Updated: %s\n", formatter_i.format (new Date (note.getUpdated ())));

			//Note objects returned by findNotes () only contain note attributes such as title, GUID, creation date, update date, etc. The note content and binary resource data are omitted, although resource metadata is included.
			//To get the note content and/or binary resources, call getNote() using the note's GUID.
			Note fullNote = noteStore_g.getNote (note.getGuid (), true, true, false, false);
			System.out.printf ("Number of resources: %d\n", fullNote.getResourcesSize ());
			System.out.println ();
		}
	}


	static String ReadFileUTF8 (File file) {
		byte [] data;
		InputStream fin = null;
		String tmp;

		try {
			fin = new FileInputStream (file);
			int filesize = (int) file.length ();
			data = new byte [filesize];
			fin.read (data, 0, filesize);
			tmp = new String (data, "UTF-8");
		}
		catch (Exception e) {
			System.out.println ("ERROR: ReadFileUTF8 - " + e.getMessage ());
			e.printStackTrace ();
			tmp = "";
		}
		finally {
			try {
				fin.close ();
			}
			catch (Exception exc) {
			}
		}
		return tmp;
	}


	static String getValue (String buf, int bufPos, String tag) {
		int begin, end;
		String tmp;

		begin = buf.indexOf ("<" + tag + ">", bufPos);
		if (begin == - 1) {
			System.out.println ("getValue: tag: <" + tag + ">: not found");
			errorCnt_g++;
			return "";
		}

		begin = begin + tag.length () + 2;

		end = buf.indexOf ("</" + tag + ">", bufPos);
		if (end == - 1) {
			System.out.println ("getValue: tag: </" + tag + ">: not found");
			errorCnt_g++;
			return "";
		}

		tmp = buf.substring (begin, end);
//		System.out.println ("getValue: " + tag + ": " + tmp);

		if (tmp.equals ("${date time}"))
			tmp = formatter_i.format (new Date ());

		return tmp;
	}


	static void doInsert (String fileName, String [] attachFileNames) throws Exception {
		String buf = ReadFileUTF8 (new File (fileName));
		int bufPos = 0;

		//To create a new note, simply create a new Note object and fill in
		//attributes such as the note's title.
		Note note = new Note ();
		note.setTitle (getValue (buf, bufPos, "title"));
		bufPos += 7 + 8 + note.getTitle ().length ();

		StringBuilder sbEnMedia = new StringBuilder ();
        String mimeType;

		for (int i = 0; i < attachFileNames.length; i++) {
//			String mimeType = "image/png";

            if (attachFileNames [i].indexOf (".mp3") > 0)
                mimeType = "audio/mpeg";
            else if (attachFileNames [i].indexOf (".jpg") > 0)
                mimeType = "image/jpeg";
            else if (attachFileNames [i].indexOf (".png") > 0)
                mimeType = "image/png";
            else
			    mimeType = "application/octet-stream";

			Resource resource = new Resource ();
			resource.setData (readFileAsData (attachFileNames [i]));
			resource.setMime (mimeType);
			ResourceAttributes attributes = new ResourceAttributes ();
			attributes.setFileName (attachFileNames [i]);
//			attributes.setAttachment (true);	//Có cũng được; không có cũng được
			resource.setAttributes (attributes);
			note.addToResources (resource);
			String hashHex = bytesToHex (resource.getData ().getBodyHash ());
			sbEnMedia.append ("<en-media type=\"" + mimeType + "\" hash=\"" + hashHex + "\" />");
		}

		StringBuilder sb = new StringBuilder ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">" +
			"<en-note>");

		sb.append (getValue (buf, bufPos, "definition"));
		if (attachFileNames.length > 0) {
			sb.append ("<div><br /></div>\n");
			sb.append (sbEnMedia.toString ());
		}

		sb.append ("</en-note>\n");
//		System.out.println (sb.toString ());
		note.setContent (sb.toString ());

		String [] arr = getValue (buf, bufPos, "tags").split (",");
		for (int i = 0; i < arr.length; i++)
			note.addToTagNames (arr [i].trim ());

		//Finally, send the new note to Evernote using the createNote method
		//The new Note object that is returned will contain server-generated
		//attributes such as the new note's unique GUID.
		Note createdNote = noteStore_g.createNote (note);
		newNoteGuid_g = createdNote.getGuid ();

		System.out.println ("Successfully created a new note with GUID: " + newNoteGuid_g);
		System.out.println ();
	}


	/***
	Retrieve and display a list of the user's notes in the specified notebook
	*/
	static void doDirNotes (String bookTitle, String titleFilter) throws Exception {
//		System.out.printf ("bookTitle: %s, titleFilter: %s", bookGuid, titleFilter);

		Notebook notebook = null;
		List<Notebook> notebooks;

		if (bookTitle.equals (""))
			notebook = noteStore_g.getDefaultNotebook ();
		else if (bookTitle.equals ("all")) {
			doDirNotes (titleFilter);
			return;
		}
		else {
			notebooks = noteStore_g.listNotebooks ();
			for (Notebook notebook_ : notebooks) {
				if (notebook_.getName ().equals (bookTitle)) {
					notebook = notebook_;
					break;
				}
			}
		}

		if (notebook == null) {
			System.out.printf ("Notebook \"%s\" is not found.\n", bookTitle);
			return;
		}

		//Next, search for the first 10000 notes in this notebook, ordering by creation date
		NoteFilter filter = new NoteFilter ();
		filter.setNotebookGuid (notebook.getGuid ());
		filter.setOrder (NoteSortOrder.CREATED.getValue ());
		filter.setAscending (true);

		if (titleFilter.equals ("") == false) {
			String query = "intitle:" + titleFilter;
			filter.setWords (query);
		}

		NoteList noteList = noteStore_g.findNotes (filter, 0, 100);
		List<Note> notes = noteList.getNotes ();
		for (Note note : notes) {
			System.out.printf ("%s %s\n", note.getGuid (), note.getTitle ());
		}
	}


	/***
	Retrieve and display a list of the user's notes in all books.
	*/
	static void doDirNotes (String titleFilter) throws Exception {
		//First, get a list of all notebooks
		List<Notebook> notebooks = noteStore_g.listNotebooks ();

		for (Notebook notebook : notebooks) {
			System.out.printf ("%s - %s\n", notebook.getGuid (), notebook.getName ());

			//Next, search for the first 10000 notes in this notebook, ordering by creation date
			NoteFilter filter = new NoteFilter ();
			filter.setNotebookGuid (notebook.getGuid ());
			filter.setOrder (NoteSortOrder.CREATED.getValue ());
			filter.setAscending (true);

			if (titleFilter.equals ("") == false) {
				String query = "intitle:" + titleFilter;
				filter.setWords (query);
			}

			NoteList noteList = noteStore_g.findNotes (filter, 0, 100);
			List<Note> notes = noteList.getNotes ();
			for (Note note : notes) {
				System.out.printf ("    %s %s\n", note.getGuid (), note.getTitle ());
			}

			System.out.println ();
		}
	}


	public static void doDirBooks () throws Exception {
		List<Notebook> notebooks = noteStore_g.listNotebooks ();

		for (Notebook notebook : notebooks) {
			System.out.println (notebook.getName ());
		}
	}


	public static void usage () {
		System.out.printf ("Usage: en <procCode> [arg...]\n");
		System.out.printf ("where owner can be binh, ta, or tam;\n");
		System.out.printf ("where procCode can be one of the following:\n");
		System.out.printf ("    dir        List all notebooks or all notes under the specified notebook\n");
		System.out.printf ("    grep       Search all notes using the specified criteria\n");
		System.out.printf ("    insert     Insert note in a specified notebook\n");
		System.out.printf ("    select     Print note by its GUID\n");
		System.out.printf ("    del        Delete note by its GUID\n");
		System.out.printf ("\n");
		System.out.printf ("Example 1.1: en dir\n");
		System.out.printf ("Example 1.2: en dir <notebook> [titlePattern]\n");
		System.out.printf ("Example 1.3: en dir \"\" [titlePattern]\n");
		System.out.printf ("             en dir \"\" Tuong\n");
		System.out.printf ("             en dir \"\" \"Tuon*\"\n");
		System.out.printf ("Example 1.4: en dir all [titlePattern]\n");
		System.out.printf ("             en dir all Tuong\n");
		System.out.printf ("             en dir all \"Tuon*\"\n");
		System.out.printf ("Example 2.1: en g[rep] title <titlePattern>\n");
		System.out.printf ("Example 2.2: en g[rep] tag <tagPattern>\n");
		System.out.printf ("Example 2.3: en g[rep] body <bodyPattern>\n");
		System.out.printf ("Example   3: en s[elect] <guid>\n");
		System.out.printf ("Example   4: en del <guid>\n");
		System.out.printf ("Example   5: en i[nsert] <notebook> <xmlFile> [<attachFile>]...\n");
		System.exit (0);
	}


	public static void main (String args []) throws Exception {
		String [] arr;

		if (args.length == 0)
			usage ();
		else if (args [0].equals ("-h") || args [0].equals ("-?"))
			usage ();

		if (token_g == null)
			token_g = System.getenv ("everNote_APIKey");

		if (token_g == null) {
			System.err.println ("Please fill in environment variable everNote_APIKey");
			System.err.println ("To get a developer token, go to https://www.evernote.com/api/DeveloperToken.action");
			return;
		}

		procCode_g = args [0];
		everNote everNote_ = new everNote (token_g);
		String dir [] = {"di", "du", "dj", "dk", "dl", "do", "ei", "wi", "si", "xi", "ci", "vi", "fi", "ri", "wu", "wo", "wj", "wk", "wl", "eu", "eo", "ej", "ek", "el", "ru", "ro", "rj", "rk", "rl", "fu", "fo", "fj", "fk", "fl", "vu", "vo", "vj", "vk", "vl", "cu", "co", "cj", "ck", "cl", "xu", "xo", "xj", "xk", "xl", "su", "so", "sj", "sk", "sl"};
		String grep [] = {"ge", "gr", "gd", "gf", "gg", "gt", "rr", "re", "rd", "rf", "rg", "rt", "te", "tr", "tt", "td", "tf", "tg", "ye", "yr", "yt", "yd", "yf", "yg", "he", "hr", "ht", "hd", "hf", "hg", "ne", "nr", "nt", "nd", "nf", "ng", "be", "br", "bt", "bd", "bf", "bg", "ve", "vr", "vt", "vd", "vf", "vg", "fe", "fr", "ft", "fd", "ff", "fg"};
		String insert [] = {"in", "ig", "ih", "ij", "ib", "im", "ug", "uh", "uj", "ub", "un", "um", "jg", "jh", "jj", "jb", "jn", "jm", "kg", "kh", "kj", "kb", "kn", "km", "lg", "lh", "lj", "lb", "ln", "lm", "og", "oh", "oj", "ob", "on", "om"};
		String select [] = {"se", "sw", "ss", "sd", "sf", "sr", "qw", "qe", "qr", "qs", "qd", "qf", "aw", "ae", "ar", "as", "ad", "af", "zw", "ze", "zr", "zs", "zd", "zf", "xe", "xr", "xt", "xd", "xf", "xg", "ce", "cr", "ct", "cd", "cf", "cg", "de", "dr", "dt", "dd", "df", "dg", "ee", "er", "et", "ed", "ef", "eg", "ww", "we", "wr", "ws", "wd", "wf"};
		String del [] = {"dw", "de", "dr", "ds", "dd", "df", "ww", "we", "wr", "ws", "wd", "wf", "sw", "se", "sr", "ss", "sd", "sf", "xw", "xe", "xr", "xs", "xd", "xf", "cw", "ce", "cr", "cs", "cd", "cf", "vw", "ve", "vr", "vs", "vd", "vf", "fw", "fe", "fr", "fs", "fd", "ff", "rw", "re", "rr", "rs", "rd", "rf", "ew", "ee", "er", "es", "ed", "ef"};

		try {
			switch (procCode_g) {
				case "dir":
					if (args.length == 1)
						doDirBooks ();
					else if (args.length == 2)
						doDirNotes (args [1], "");
					else
						doDirNotes (args [1], args [2]);

					break;
				
				case "g":
				case "grep":
					if (args.length < 2)
						usage ();
					else if (args.length < 3)
						doGrep ("body", args [1]);
					else
						doGrep (args [1], args [2]);

					break;

				case "s":
				case "select":
					if (args.length < 2)
						usage ();

					doSelect (args [1]);
					break;
				
				case "del":
				  if (args.length < 2)
				    usage ();
	  
				  doDelete (args [1]);
				  break;

				case "i":
				case "insert":
					if (args.length < 3)
						usage ();

					if (args.length == 3)
						arr = new String [] { };
					else {
						arr = new String [args.length - 3];
						for (int i = 3; i < args.length; i++)
							arr [i - 3] = args [i];
					}
						
					doInsert (args [2], arr);
					break;
				
				case "h":
				case "help":
				  	usage ();
					break;

				default:
					System.out.println ("Unrecognized command: " + procCode_g);
					for (int i = 0; i < dir.length; ++i) {
						if (procCode_g.contains (dir [i])) {
							System.out.println ("Perhaps you meant: dir\n");
							usage ();
							break;
						}
						else if (procCode_g.contains (grep[i])) {
							System.out.println ("Perhaps you meant: g[rep]\n");
							usage ();
							break;
						}
						else if (procCode_g.contains (insert [i])) {
						    System.out.println ("Perhaps you meant: i[nsert]\n");
						    usage ();
						    break;
						}
						else if (procCode_g.contains (select [i])) {
							System.out.println ("Perhaps you meant: s[elect]\n");
							usage ();
							break;
						}
						else if (procCode_g.contains (del[i])) {
							System.out.println ("Perhaps you meant: del\n");
							usage ();
							break;
						}
					}

					usage ();
					return;
			}
		}
		catch (EDAMUserException exc) {
			//These are the most common error types that you'll need to handle
			//EDAMUserException is thrown when an API call fails because a parameter was invalid.
			if (exc.getErrorCode () == EDAMErrorCode.AUTH_EXPIRED)
				System.err.println ("Your authentication token is expired!");
			else if (exc.getErrorCode () == EDAMErrorCode.INVALID_AUTH)
				System.err.println ("Your authentication token is invalid!");
			else if (exc.getErrorCode () == EDAMErrorCode.QUOTA_REACHED)
				System.err.println ("Your authentication token is invalid!");
			else
				System.err.println ("Error: " + exc.getErrorCode ().toString () + "; Parameter: " + exc.getParameter ());
		}
		catch (EDAMSystemException exc) {
			System.err.println ("System error: " + exc.getErrorCode ().toString ());
		}
		catch (TTransportException exc) {
			System.err.println ("Networking error: " + exc.getMessage ());
		}

/***
    try {
      everNote_.listNotes ();
      everNote_.createNote ();
      everNote_.search ();
      everNote_.updateNoteTag ();
    }
	
***/
    }

}

/***
DỊCH:
	javac -cp evernote_api.jar -encoding utf8 everNote.java
	java -cp evernote_api.jar;. -Dfile.encoding=UTF-8 everNote

GHI CHÚ:
1.	Real applications authenticate with Evernote using OAuth, but for the
	purpose of exploring the API, you can get a developer token that allows
	you to access your own Evernote account. To get a developer token, visit
	https://sandbox.evernote.com/api/DeveloperToken.action

2.	http://dev.evernote.com/doc/reference/javadoc/

3.	Be mindful là khi argument là * thì Java (hoặc shell) sẽ bành trướng * thành list of files ở current directory.
	VD:
	System.out.printf ("Example 1.3: en d[ir] * [condition]\n");

	Khi PQB chạy
	java -cp evernote_api.jar;. everNote dir *

	thì hàm doDirNotes (String bookGuid, String condition) báo cáo:
	bookGuid: EDAMDemo.class, condition: EDAMDemo.java

4.	Supported mimetype
	image/gif
	image/jpeg
	image/png
	audio/wav
	audio/mpeg
	audio/amr
	application/pdf

	Nguồn: http://dev.evernote.com/doc/articles/resources.php#types


LỊCH SỬ:
1.00	20170826	PQB
		Dùng EDAMDemo.java làm sườn để xây lên. Dùng tiêu chuẩn của batnha.cs, ih.cs, tdput.java, resBuilder.mssql.cs.

***/

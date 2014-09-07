package com.solvebio.xmlsplit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import com.google.common.collect.Lists;

public class XmlSplit {
	public static void main(String[] args) throws IOException {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("XmlSplit").defaultHelp(true)
				.description("Split a single, large XML document into 'N' smaller documents.");
		parser.addArgument("-n", "--number").type(Integer.class).setDefault(5).help("number of output files");
		parser.addArgument("inputFilepath").metavar("[ input filepath ]").help("input XML filepath");
		parser.addArgument("outputDirectory").metavar("[ output directory ]").help("root XML output directory");
		parser.addArgument("tag").metavar("[ element tag ]").help("the element to split on");
		Namespace ns = null;
		try {
			ns = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}

		long t0 = System.currentTimeMillis();

		// setup paths
		String inputFilepath = ns.getString("inputFilepath");
		String outputDirectory = ns.getString("outputDirectory");
		String filename = FilenameUtils.getBaseName(inputFilepath);
		String ext = "." + FilenameUtils.getExtension(inputFilepath);
		String outputFilepathFmt = FilenameUtils.concat(outputDirectory, filename + "_%02d" + ext);

		// ensure output director
		FileUtils.forceMkdir(new File(outputDirectory));

		// setup writers
		int writerIdx = 0;
		Writer[] fileWriters = new BufferedWriter[ns.get("number")];
		for (int i = 0; i < fileWriters.length; i++) {
			String fname = String.format(outputFilepathFmt, i);
			System.out.println("Creating output file: " + fname);
			fileWriters[i] = new BufferedWriter(new FileWriter(fname));
		}

		String tag = ns.getString("tag").replace("<", "").replace(">", "");
		List<String> header = Lists.newArrayList();
		List<String> lines = Lists.newArrayList();
		List<String> footer = Lists.newArrayList();

		int readCnt = 0;
		int elemCnt = 0;
		LineIterator iter = FileUtils.lineIterator(new File(inputFilepath));
		boolean foundOne = false;
		while (iter.hasNext()) {
			if (++readCnt % 1000000 == 0) {
				System.out.println(String.format("read %s lines", readCnt));
			}

			// sanity check. if we've gone 10,000 lines without hitting an opening tag, puke
			if (readCnt == 10000 && !foundOne) {
				System.out.println(String.format("Couldn't find opening tag '%s' after 10K lines. Exiting.", tag));
				System.exit(1);
			}

			String line = iter.nextLine();
			String lineTrim = line.trim();

			// skip empty lines
			if (lineTrim.isEmpty()) {
				continue;
			}

			// populate header
			if (!foundOne && !lineTrim.startsWith("<" + tag)) {
				header.add(line);
				continue;
			} else {
				if (!foundOne) {
					// write headers for all files
					System.out.println("writing headers...");
					for (Writer w : fileWriters) {
						IOUtils.writeLines(header, IOUtils.LINE_SEPARATOR, w);
					}
				}
				foundOne = true;
			}

			lines.add(line);
			if (lineTrim.startsWith("</" + tag)) {
				if (++elemCnt % 10000 == 0) {
					System.out.println(String.format("processed %s elements", elemCnt));
				}

				// write
				IOUtils.writeLines(lines, IOUtils.LINE_SEPARATOR, fileWriters[(++writerIdx + 1) % fileWriters.length]);

				// round-robin
				// writerIdx = (writerIdx + 1) % fileWriters.length;

				lines.clear();
				footer.clear();
			} else {
				footer.add(line);
			}
		}

		// sanity check. if we've gone 10,000 lines without hitting an opening tag, puke
		if (!foundOne) {
			System.out.println(String.format("Finished reading the file but couldn't find opening tag '%s'. Exiting.",
					tag));
			// TODO: cleanup outputs
			System.exit(1);
		}

		// write footer!
		System.out.println("writing footers...");
		for (Writer w : fileWriters) {
			IOUtils.writeLines(footer, IOUtils.LINE_SEPARATOR, w);
		}

		// close all writers
		for (Writer w : fileWriters) {
			w.close();
		}

		long t1 = System.currentTimeMillis();
		System.out.println(String.format("done! read %s lines. processed %s elements. took: %s ms.", readCnt, elemCnt,
				t1 - t0));
	}
}

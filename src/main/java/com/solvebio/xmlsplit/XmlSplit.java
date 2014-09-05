package com.solvebio.xmlsplit;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import com.google.common.collect.Lists;

public class XmlSplit {
	public static void main(String[] args) throws IOException {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("XmlSplit").defaultHelp(true)
				.description("Split a single, large XML document into 'N' smaller documents.");
		parser.addArgument("-n", "--number").type(Integer.class).setDefault(10).help("number of output files");
		parser.addArgument("input_filepath").metavar("[ input filepath ]").help("input XML filepath");
		parser.addArgument("output_directory").metavar("[ output directory ]").help("root XML output directory");
		parser.addArgument("tag").metavar("[ element tag ]").help("the element to split on");
		Namespace ns = null;
		try {
			ns = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}

		long t0 = System.currentTimeMillis();

		int fileIdx = 0;
		File[] output_files = new File[ns.get("number")];
		for (int i = 0; i < output_files.length; i++) {
			output_files[i] = new File(String.format("./sample_output/%s.xml", i));
		}

		String tag = ns.getString("tag").replace("<", "").replace(">", "");
		List<String> header = Lists.newArrayList();
		List<String> lines = Lists.newArrayList();
		List<String> footer = Lists.newArrayList();

		int readCnt = 0;
		int elemCnt = 0;
		LineIterator iter = FileUtils.lineIterator(new File(ns.getString("input_filepath")));
		boolean foundOne = false;
		while (iter.hasNext()) {
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
					// write headers for all files!
					for (File f : output_files) {
						System.out.println("writing header to " + f.getName());
						FileUtils.writeLines(f, header, true);
					}
				}
				foundOne = true;
			}

			lines.add(line);
			if (lineTrim.startsWith("</" + tag)) {
				elemCnt++;
				if (elemCnt % 100 == 0) {
					// TODO: make this configurable!
					// flush every 100 elements
					FileUtils.writeLines(output_files[fileIdx], lines, true);

					// TODO: add some debugging/progress print statement here!
					System.out.println(String.format(
							"writing 100 objects (%s lines) to " + output_files[fileIdx].getName(), lines.size()));

					// clear
					lines.clear();

					// round-robin
					fileIdx = (fileIdx + 1) % output_files.length;
				}

				footer.clear();
			} else {
				footer.add(line);
			}

			readCnt++;

			if (readCnt % 1000000 == 0) {
				System.out.println(String.format("read %s lines", readCnt));
			}
		}

		// dump remaining lines to a single file
		List<String> linesRemaining = lines.subList(0, lines.size() - footer.size());
		System.out.println(String.format("writing %s objects (%s lines) to " + output_files[fileIdx].getName(),
				elemCnt % 100, linesRemaining.size()));
		FileUtils.writeLines(output_files[fileIdx], linesRemaining, true);

		// write footer!
		for (File f : output_files) {
			System.out.println("writing footer to " + f.getName());
			FileUtils.writeLines(f, Lists.reverse(footer), true);
		}

		long t1 = System.currentTimeMillis();
		System.out.println(String.format("done! took: %s ms.", t1 - t0));
	}
}

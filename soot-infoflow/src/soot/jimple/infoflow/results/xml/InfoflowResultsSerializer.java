package soot.jimple.infoflow.results.xml;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import soot.jimple.Stmt;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.AccessPathFragment;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.results.InfoflowPerformanceData;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.MethodSourceSinkDefinition;

/**
 * Class for serializing FlowDroid results to XML
 * 
 * @author Steven Arzt
 *
 */
public class InfoflowResultsSerializer {

	public static final int FILE_FORMAT_VERSION = 102;

	protected boolean serializeTaintPath = true;
	protected IInfoflowCFG icfg;
	protected InfoflowConfiguration config;
	protected long startTime = 0;

	/**
	 * Creates a new instance of the InfoflowResultsSerializer class
	 * 
	 * @param config The configuration of the data flow
	 */
	public InfoflowResultsSerializer(InfoflowConfiguration config) {
		this(null, config);
	}

	/**
	 * Creates a new instance of the InfoflowResultsSerializer class
	 * 
	 * @param cfg    The control flow graph to be used for obtaining additional
	 *               information such as the methods containing source or sink
	 *               statements
	 * @param config The configuration of the data flow
	 */
	public InfoflowResultsSerializer(IInfoflowCFG cfg, InfoflowConfiguration config) {
		this.icfg = cfg;
		this.config = config;
	}

	/**
	 * Serializes the given FlowDroid result object into the given file
	 * 
	 * @param results  The result object to serialize
	 * @param fileName The target file name
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 * @throws IOException        Thrown if the target file could not be written
	 */
	public void serialize(InfoflowResults results, String fileName) throws XMLStreamException, IOException {
		this.startTime = System.currentTimeMillis();
		try (OutputStream out = new FileOutputStream(fileName)) {
			XMLOutputFactory factory = XMLOutputFactory.newInstance();
			XMLStreamWriter writer = factory.createXMLStreamWriter(out, "UTF-8");

			writer.writeStartDocument("UTF-8", "1.0");
			writer.writeStartElement(XmlConstants.Tags.root);
			writer.writeAttribute(XmlConstants.Attributes.fileFormatVersion, FILE_FORMAT_VERSION + "");
			writer.writeAttribute(XmlConstants.Attributes.terminationState,
					terminationStateToString(results.getTerminationState()));

			// Write out the data flow results
			if (results != null && !results.isEmpty()) {
				writer.writeStartElement(XmlConstants.Tags.results);
				writeDataFlows(results, writer);
				writer.writeEndElement();
			}

			// Write out performance data
			InfoflowPerformanceData performanceData = results.getPerformanceData();
			if (performanceData != null && !performanceData.isEmpty()) {
				writer.writeStartElement(XmlConstants.Tags.performanceData);
				writePerformanceData(performanceData, writer);
				writer.writeEndElement();
			}

			writer.writeEndDocument();
			writer.close();
		}
	}

	/**
	 * Converts the termination state from the enumeration to a human-readable
	 * string
	 * 
	 * @param terminationState The termination state
	 * @return A human-readable version of the termination state
	 */
	private String terminationStateToString(int terminationState) {
		switch (terminationState) {
		case InfoflowResults.TERMINATION_SUCCESS:
			return "Success";
		case InfoflowResults.TERMINATION_DATA_FLOW_TIMEOUT:
			return "DataFlowTimeout";
		case InfoflowResults.TERMINATION_DATA_FLOW_OOM:
			return "DataFlowOutOfMemory";
		case InfoflowResults.TERMINATION_PATH_RECONSTRUCTION_TIMEOUT:
			return "PathReconstructionTimeout";
		case InfoflowResults.TERMINATION_PATH_RECONSTRUCTION_OOM:
			return "PathReconstructionOfMemory";
		default:
			return "Unknown";
		}
	}

	/**
	 * Writes out the given data flow performance data into the given XML stream
	 * writer
	 * 
	 * @param performanceData The performance data to write out
	 * @param writer          The stream writer into which to write the data
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	private void writePerformanceData(InfoflowPerformanceData performanceData, XMLStreamWriter writer)
			throws XMLStreamException {
		writePerformanceEntry(XmlConstants.Values.PERF_CALLGRAPH_SECONDS,
				performanceData.getCallgraphConstructionSeconds(), writer);
		writePerformanceEntry(XmlConstants.Values.PERF_TAINT_PROPAGATION_SECONDS,
				performanceData.getTaintPropagationSeconds(), writer);
		writePerformanceEntry(XmlConstants.Values.PERF_PATH_RECONSTRUCTION_SECONDS,
				performanceData.getPathReconstructionSeconds(), writer);
		writePerformanceEntry(XmlConstants.Values.PERF_TOTAL_RUNTIME_SECONDS, performanceData.getTotalRuntimeSeconds(),
				writer);
		writePerformanceEntry(XmlConstants.Values.PERF_MAX_MEMORY_CONSUMPTION,
				performanceData.getMaxMemoryConsumption(), writer);

		writePerformanceEntry(XmlConstants.Values.PERF_SOURCE_COUNT, performanceData.getSourceCount(), writer);
		writePerformanceEntry(XmlConstants.Values.PERF_SINK_COUNT, performanceData.getSinkCount(), writer);
	}

	/**
	 * Writes a single performance data entry into the XML file. An entry has a name
	 * and a value, where the name describes the performance metric.
	 * 
	 * @param entryName  The name that describes the performance metric
	 * @param entryValue The value of the performance metric
	 * @param writer     The stream writer into which to write the data
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	private void writePerformanceEntry(String entryName, int entryValue, XMLStreamWriter writer)
			throws XMLStreamException {
		if (entryValue > 0) {
			writer.writeStartElement(XmlConstants.Tags.performanceEntry);
			writer.writeAttribute(XmlConstants.Attributes.name, entryName);
			writer.writeAttribute(XmlConstants.Attributes.value, entryValue + "");
			writer.writeEndElement();
		}
	}

	/**
	 * Writes the given data flow results into the given XML stream writer
	 * 
	 * @param results The results to write out
	 * @param writer  The stream writer into which to write the results
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	protected void writeDataFlows(InfoflowResults results, XMLStreamWriter writer) throws XMLStreamException {
		for (ResultSinkInfo sink : results.getResults().keySet()) {
			writer.writeStartElement(XmlConstants.Tags.result);
			writeSinkInfo(sink, writer);

			// Write out the sources
			writer.writeStartElement(XmlConstants.Tags.sources);
			for (ResultSourceInfo src : results.getResults().get(sink))
				writeSourceInfo(src, writer);
			writer.writeEndElement();

			writer.writeEndElement();
		}

	}

	/**
	 * Writes the given source information into the given XML stream writer
	 * 
	 * @param source The source information to write out
	 * @param writer The stream writer into which to write the results
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	private void writeSourceInfo(ResultSourceInfo source, XMLStreamWriter writer) throws XMLStreamException {
		writer.writeStartElement(XmlConstants.Tags.source);
		writer.writeAttribute(XmlConstants.Attributes.statement, source.getStmt().toString());
		if (config.getEnableLineNumbers())
			writer.writeAttribute(XmlConstants.Attributes.linenumber,
					String.valueOf(source.getStmt().getJavaSourceStartLineNumber()));
		if (source.getDefinition().getCategory() != null)
			writer.writeAttribute(XmlConstants.Attributes.category,
					source.getDefinition().getCategory().getHumanReadableDescription());
		if (icfg != null)
			writer.writeAttribute(XmlConstants.Attributes.method, icfg.getMethodOf(source.getStmt()).getSignature());
		ISourceSinkDefinition def = source.getDefinition();
		if (def instanceof MethodSourceSinkDefinition) {
			MethodSourceSinkDefinition ms = (MethodSourceSinkDefinition) def;
			if (ms.getMethod() != null)
				writer.writeAttribute(XmlConstants.Attributes.methodSourceSinkDefinition, ms.getMethod().getSignature());
		}

		// TODO：如果Source方法是curser的getString，检查其用到的URI
		SootMethod method = icfg.getMethodOf(source.getStmt());
		if (method.getDeclaringClass().getName().equals("android.database.Cursor")) {
			if (method.getName().equals("getString")) {
				// 寻找getColumIndex的调用
				SootMethod caller = icfg.getMethodOf(source.getStmt());
				// 遍历caller的所有语句
				for (Unit unit : caller.getActiveBody().getUnits()) {
					// 如果是invoke语句
					if (unit.toString().contains("invoke")) {
						// 获取invoke的方法
						SootMethod invokeMethod = ((Stmt) unit).getInvokeExpr().getMethod();
						// 如果是getColumnIndex
						if (invokeMethod.getName().equals("getColumnIndex")) {
							// TODO：获取URI参数
						}
					}
				}
			}
		}

		writeAdditionalSourceInfo(source, writer);
		writeAccessPath(source.getAccessPath(), writer);

		if (serializeTaintPath && source.getPath() != null) {
			writer.writeStartElement(XmlConstants.Tags.taintPath);
			for (int i = 0; i < source.getPath().length; i++) {
				writer.writeStartElement(XmlConstants.Tags.pathElement);

				Stmt curStmt = source.getPath()[i];
				writer.writeAttribute(XmlConstants.Attributes.statement, curStmt.toString());

				if (icfg != null)
					writer.writeAttribute(XmlConstants.Attributes.method, icfg.getMethodOf(curStmt).getSignature());

				AccessPath curAP = source.getPathAccessPaths()[i];
				writeAccessPath(curAP, writer);

				writer.writeEndElement();
			}
			writer.writeEndElement();
		}

		writer.writeEndElement();
	}

	/**
	 * Derived classes can override this method to write out additional information
	 * about a data flow source
	 * 
	 * @param source The source information to write out
	 * @param writer The stream writer into which to write the results
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	protected void writeAdditionalSourceInfo(ResultSourceInfo source, XMLStreamWriter writer)
			throws XMLStreamException {
		//
	}

	/**
	 * Writes the given sink information into the given XML stream writer
	 * 
	 * @param sink   The sink information to write out
	 * @param writer The stream writer into which to write the results
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	private void writeSinkInfo(ResultSinkInfo sink, XMLStreamWriter writer) throws XMLStreamException {
		writer.writeStartElement(XmlConstants.Tags.sink);
		writer.writeAttribute(XmlConstants.Attributes.statement, sink.getStmt().toString());
		if (config.getEnableLineNumbers())
			writer.writeAttribute(XmlConstants.Attributes.linenumber,
					String.valueOf(sink.getStmt().getJavaSourceStartLineNumber()));
		if (sink.getDefinition().getCategory() != null)
			writer.writeAttribute(XmlConstants.Attributes.category,
					sink.getDefinition().getCategory().getHumanReadableDescription());
		if (icfg != null)
			writer.writeAttribute(XmlConstants.Attributes.method, icfg.getMethodOf(sink.getStmt()).getSignature());
		ISourceSinkDefinition def = sink.getDefinition();
		if (def instanceof MethodSourceSinkDefinition) {
			MethodSourceSinkDefinition ms = (MethodSourceSinkDefinition) def;
			if (ms.getMethod() != null)
				writer.writeAttribute(XmlConstants.Attributes.methodSourceSinkDefinition, ms.getMethod().getSignature());
		}
		writeAdditionalSinkInfo(sink, writer);
		writeAccessPath(sink.getAccessPath(), writer);
		writer.writeEndElement();
	}

	/**
	 * Derived classes can override this method to write out additional information
	 * about a data flow sink
	 * 
	 * @param sink   The sink information to write out
	 * @param writer The stream writer into which to write the results
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	protected void writeAdditionalSinkInfo(ResultSinkInfo sink, XMLStreamWriter writer) throws XMLStreamException {
		//
	}

	/**
	 * Writes the given access path into the given XML stream writer
	 * 
	 * @param accessPath The access path to write out
	 * @param writer     The stream writer into which to write the data
	 * @throws XMLStreamException Thrown if the XML data cannot be written
	 */
	protected void writeAccessPath(AccessPath accessPath, XMLStreamWriter writer) throws XMLStreamException {
		writer.writeStartElement(XmlConstants.Tags.accessPath);

		if (accessPath.getPlainValue() != null)
			writer.writeAttribute(XmlConstants.Attributes.value, accessPath.getPlainValue().toString());
		if (accessPath.getBaseType() != null)
			writer.writeAttribute(XmlConstants.Attributes.type, accessPath.getBaseType().toString());
		writer.writeAttribute(XmlConstants.Attributes.taintSubFields,
				accessPath.getTaintSubFields() ? XmlConstants.Values.TRUE : XmlConstants.Values.FALSE);

		// Write out the fields
		if (accessPath.getFragmentCount() > 0) {
			writer.writeStartElement(XmlConstants.Tags.fields);
			for (int i = 0; i < accessPath.getFragmentCount(); i++) {
				writer.writeStartElement(XmlConstants.Tags.field);
				AccessPathFragment fragment = accessPath.getFragments()[i];
				writer.writeAttribute(XmlConstants.Attributes.value, fragment.getField().toString());
				writer.writeAttribute(XmlConstants.Attributes.type, fragment.getFieldType().toString());
				writer.writeEndElement();
			}
			writer.writeEndElement();
		}

		writer.writeEndElement();
	}

	/**
	 * Sets whether the taint propagation path shall be serialized along with the
	 * respective data flow result
	 * 
	 * @param serialize True if taint propagation paths shall be serialized,
	 *                  otherwise false
	 */
	public void setSerializeTaintPath(boolean serialize) {
		this.serializeTaintPath = serialize;
	}

}

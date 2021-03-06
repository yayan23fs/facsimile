package edu.stanford.bmir.facsimile.dbq.configuration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.semanticweb.owlapi.model.IRI;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.bmir.facsimile.dbq.exception.ConfigurationFileParseException;
import edu.stanford.bmir.facsimile.dbq.exception.MissingConfigurationElementException;
import edu.stanford.bmir.facsimile.dbq.form.elements.FormElement.ElementType;
import edu.stanford.bmir.facsimile.dbq.form.elements.FormElementList;
import edu.stanford.bmir.facsimile.dbq.form.elements.FormElementList.FormElementListType;
import edu.stanford.bmir.facsimile.dbq.form.elements.Section.SectionType;
import edu.stanford.bmir.facsimile.dbq.tree.TreeNode;

/**
 * @author Rafael S. Goncalves <br>
 * Stanford Center for Biomedical Informatics Research (BMIR) <br>
 * School of Medicine, Stanford University <br>
 */
public class Configuration {
	private Document doc;
	private String ontPath, outPath, title, cssStyle, formIri;
	private Map<IRI,String> imports;
	private Map<IRI,IRI> subquestionPosTriggers, subquestionNegTriggers;
	private Map<IRI,ElementType> elementTypes;
	private Map<IRI,SectionType> sectionTypes;
	private Map<IRI,List<TreeNode<IRI>>> sections;
	private Map<IRI,List<String>> optionsOrder;
	private Map<IRI,Boolean> sectionNumbering, questionNumbering, questionRequired;
	private List<FormElementList> elementLists;
	private final String delim = ";";
	private File file;
	private boolean verbose;
	
	
	/**
	 * Constructor
	 * @param file	XML document file
	 * @param verbose	true for verbose mode
	 */
	public Configuration(File file, boolean verbose) {
		this.file = file;
		this.verbose = verbose;
		imports = new HashMap<IRI,String>();
		subquestionPosTriggers = new HashMap<IRI,IRI>();
		subquestionNegTriggers = new HashMap<IRI,IRI>();
		elementTypes = new LinkedHashMap<IRI,ElementType>();
		sectionTypes = new LinkedHashMap<IRI,SectionType>();
		optionsOrder = new HashMap<IRI,List<String>>();
		sectionNumbering = new HashMap<IRI,Boolean>();
		questionNumbering = new HashMap<IRI,Boolean>();
		questionRequired = new HashMap<IRI,Boolean>();
		elementLists = new ArrayList<FormElementList>();
	}
	
	
	/**
	 * Constructor 
	 * @param file	XML document file
	 */
	public Configuration(File file) {
		this(file, false);
	}
	
	
	/**
	 * Load configuration file and initialize data structures
	 */
	public void parseConfigurationFile() {
		doc = loadConfigurationFile(file);
		if(doc == null) throw new ConfigurationFileParseException("An error occurred parsing the given XML configuration file. "
				+ "Ensure that the file is well-formed and valid with respect to the given DTD");
		formIri = getFormIRI();
		sections = getSections();
		gatherInputInformation();
		gatherOutputInformation();
	}
	
	
	/**
	 * Load XML configuration file 
	 * @param f	File
	 * @return XML document instance 
	 */
	private Document loadConfigurationFile(File f) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document doc = null;
		try {
			db = dbf.newDocumentBuilder();
		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		}
		try {
			doc = db.parse(f);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
		return doc;
	}
	
	
	/**
	 * Retrieve output information
	 */
	private void gatherOutputInformation() {
		NodeList nl = doc.getElementsByTagName("output");
		for(int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if(n.hasChildNodes() && n.getChildNodes().getLength() > 0) {
				for(int j = 0; j < n.getChildNodes().getLength(); j++) {
					Node c = n.getChildNodes().item(j);
					if(c.getNodeName().equalsIgnoreCase("file")) {
						outPath = c.getTextContent();
						if(c.hasAttributes() && c.getAttributes().getNamedItem("title") != null)
							title = c.getAttributes().getNamedItem("title").getTextContent();
					}
					if(c.getNodeName().equalsIgnoreCase("cssstyle"))
						cssStyle = c.getTextContent();
				}
			}
		}
	}
	
	
	/**
	 * Retrieve ontology files (including imports)
	 */
	private void gatherInputInformation() {
		NodeList nl = doc.getElementsByTagName("ontology");
		for(int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if(n.getParentNode().getNodeName().equalsIgnoreCase("imports")) {
				if(n.hasAttributes()) {
					String iri = n.getAttributes().getNamedItem("iri").getTextContent();
					if(iri != null)
						imports.put(IRI.create(iri), n.getTextContent());
				}
			}
			else if(n.getParentNode().getNodeName().equalsIgnoreCase("input"))
				ontPath = n.getTextContent();
		}
	}
	
	
	/**
	 * Get the IRI of the individual representing the form
	 * @return String representation of the form individual IRI
	 */
	private String getFormIRI() {
		String formIri = "";
		NodeList nl = doc.getElementsByTagName("form");
		loop:
			for(int i = 0; i < nl.getLength(); i++) {
				NodeList nodeList = nl.item(i).getChildNodes();
				for(int j = 0; j < nodeList.getLength(); j++) {
					if(nodeList.item(j).getNodeName().equalsIgnoreCase("iri")) {
						formIri = nodeList.item(j).getTextContent();
						if(verbose) System.out.println(" Form IRI: " + formIri);
						break loop;
					}
				}
			}
		if(formIri.isEmpty()) throw new MissingConfigurationElementException("IRI of form individual is missing");
		return formIri;
	}
	
	
	/**
	 * Gather a map of sections specified in the configuration file and their questions
	 * @return Map of sections' IRIs and their respective questions
	 */
	private Map<IRI,List<TreeNode<IRI>>> getSections() {
		Map<IRI,List<TreeNode<IRI>>> sections = new LinkedHashMap<IRI,List<TreeNode<IRI>>>();
		NodeList nl = doc.getElementsByTagName("section");
		for(int i = 0; i < nl.getLength(); i++) { // foreach section
			Node sectionNode = nl.item(i);
			SectionType type = getSectionType(sectionNode);
			NodeList children = sectionNode.getChildNodes(); // <iri>, (<questionList> | <infoList>)
			List<TreeNode<IRI>> questions = null; IRI sectionIri = null;
			for(int j = 0; j < children.getLength(); j++) {
				Node child = children.item(j);
				if(child.getNodeName().equalsIgnoreCase("iri")) { // section iri
					sectionIri = IRI.create(child.getTextContent());
					if(verbose) System.out.println("   Section: " + sectionIri + " (type: " + type.toString() + ")");
				}
				else if(child.getNodeName().equalsIgnoreCase("questionlist"))
					questions = getQuestions(sectionIri, child, null, null);
				else if(child.getNodeName().equalsIgnoreCase("infolist"))
					questions = getInfoRequests(child);
			}
			if(sectionIri != null && questions != null && !questions.isEmpty()) {
				sections.put(sectionIri, questions);
				sectionNumbering.put(sectionIri, isSectionNumbered(sectionNode));
				sectionTypes.put(sectionIri, type);
			}
		}
		return sections;
	}
	
	
	/**
	 * Get the section type of a given section node
	 * @param n	Section node
	 * @return Section type
	 */
	private SectionType getSectionType(Node n) {
		SectionType type = null;
		if(n.hasAttributes() && n.getAttributes().getNamedItem("type") != null) {
			Node a = n.getAttributes().getNamedItem("type");
			for(SectionType s : SectionType.values())
				if(a.getTextContent().equalsIgnoreCase(s.toString()))
					type = s;
		}
		else
			type = SectionType.QUESTION_SECTION;
		return type;
	}
	
	
	/**
	 * Check whether a given section is declared to be un-numbered
	 * @param n	Section node
	 * @return true if section is numbered, false otherwise
	 */
	private boolean isSectionNumbered(Node n) {
		boolean isNumbered = true;
		if(n.hasAttributes()) {
			Node a = n.getAttributes().getNamedItem("numbered");
			if(a != null)
				isNumbered = Boolean.parseBoolean(a.getTextContent());
		}
		return isNumbered;
	}
	
	
	/**
	 * Gather the questions in a given questionlist node
	 * @param siblingIri	IRI of the question which is a sibling of the given question-list 
	 * @param questionListNode	Questionlist node
	 * @param questionTree	Question treenode, if applicable
	 * @param parentQuestionList	Parent question list
	 * @return List of question trees
	 */
	private List<TreeNode<IRI>> getQuestions(IRI siblingIri, Node questionListNode, TreeNode<IRI> questionTree, List<IRI> parentQuestionList) {
		List<TreeNode<IRI>> questions = new ArrayList<TreeNode<IRI>>();
		List<IRI> questionList = new ArrayList<IRI>();
		if(parentQuestionList == null) parentQuestionList = new ArrayList<IRI>();
		NodeList nl = questionListNode.getChildNodes(); // <question>'s
		for(int i = 0; i < nl.getLength(); i++) { // foreach <question>
			IRI iri = null;
			Node qNode = nl.item(i);
			NodeList children = qNode.getChildNodes(); // (<iri> | <questionList>)
			TreeNode<IRI> subquestions = questionTree;
			for(int j = 0; j < children.getLength(); j++) {
				Node curNode = children.item(j);
				if(curNode.getNodeName().equalsIgnoreCase("iri")) {
					iri = getQuestionIRI(curNode, null);
					if(iri != null) {
						if(subquestions == null) subquestions = new TreeNode<IRI>(iri);
						else subquestions = subquestions.addChild(iri);
						checkAttributes(iri, qNode);
						questionList.add(iri); parentQuestionList.add(iri);
					}
				}
				else if(curNode.getNodeName().equalsIgnoreCase("questionlist")) // (sub-)<questionList>
					getQuestions(iri, curNode, subquestions, questionList);
			}
			if(subquestions != null)
				questions.add(subquestions);
		}
		createFormElementList(questionListNode, questionList, siblingIri);
		return questions;
	}
	
	
	/**
	 * Create and add a QuestionList instance
	 * @param questionListNode	XML questionList node
	 * @param questionList	List of question IRIs in this node
	 * @param iri	IRI of the question (or section) from which the form element list is derived
	 */
	private void createFormElementList(Node questionListNode, List<IRI> questionList, IRI iri) {
		String id = "fail"; 
		if(iri != null) id = iri.getShortForm().toString();
		FormElementList ql = new FormElementList(questionList, id);
		String type = getAttribute(questionListNode, "type");
		String repeat = getAttribute(questionListNode, "repeat");
		int reps = 0;
		if(repeat != null && Integer.parseInt(repeat)>0)
			reps = Integer.parseInt(repeat);
		if(type != null && type.equals("inline"))
			if(reps > 0) {
				ql.setType(FormElementListType.INLINEREPEATED);
				ql.setRepetitions(reps);
			}
			else
				ql.setType(FormElementListType.INLINE);
		else if(reps > 0){
			ql.setType(FormElementListType.REPEATED);
			ql.setRepetitions(reps);
		}
		ql.setDepth(getDepth(questionListNode));
		elementLists.add(ql);
	}
	
	
	/**
	 * Get depth of a node w.r.t. the root node
	 * @param node	Node to check depth of
	 * @return Depth of given node
	 */
	private int getDepth(Node node) {
		int depth = 0;
		while(node.getParentNode() != null) {
			depth++;
			node = node.getParentNode();
		}
		return depth;
	}
	
	
	/**
	 * Get the text content of the specified attribute within a given node, if such an attribute exists, otherwise this returns null
	 * @param node	Node
	 * @param attribute	Attribute name
	 * @return Text content of the given attribute in the specified node
	 */
	private String getAttribute(Node node, String attribute) {
		if(node.hasAttributes())
			if(node.getAttributes().getNamedItem(attribute) != null)
				return node.getAttributes().getNamedItem(attribute).getTextContent();
		return null;
	}
	
	
	/**
	 * Check attributes of a given question node
	 * @param iri	Question IRI
	 * @param qNode	Question node
	 */
	private void checkAttributes(IRI iri, Node qNode) {
		boolean numbered = true, required = false;
		if(qNode.hasAttributes()) {
			NamedNodeMap nodeMap = qNode.getAttributes();
			if(nodeMap.getNamedItem("numbered") != null)
				numbered = Boolean.parseBoolean(nodeMap.getNamedItem("numbered").getTextContent());
			if(nodeMap.getNamedItem("required") != null)
				required = Boolean.parseBoolean(nodeMap.getNamedItem("required").getTextContent());
			if(nodeMap.getNamedItem("showSubquestionsForAnswer") != null)
				subquestionPosTriggers.put(iri, IRI.create(nodeMap.getNamedItem("showSubquestionsForAnswer").getNodeValue()));
			if(nodeMap.getNamedItem("hideSubquestionsForAnswer") != null)
				subquestionNegTriggers.put(iri, IRI.create(nodeMap.getNamedItem("hideSubquestionsForAnswer").getNodeValue()));
			if(nodeMap.getNamedItem("optionOrder") != null) {
				String order = nodeMap.getNamedItem("optionOrder").getNodeValue();
				StringTokenizer tokenizer = new StringTokenizer(order, delim);
				List<String> list = new ArrayList<String>();
				while(tokenizer.hasMoreTokens())
					list.add(tokenizer.nextToken());
				optionsOrder.put(iri, list);
			}
		}
		questionNumbering.put(iri, numbered);
		questionRequired.put(iri, required);
	}
	
	
	/**
	 * Add subquestion (tree) trigger(s) given by the attribute node
	 * @param map	Map of question IRIs to lists of question IRIs which trigger subquestions
	 * @param iri	Question IRI
	 * @param n	Attribute node
	 */
	@SuppressWarnings("unused")
	private void addSubquestionTriggers(Map<IRI,List<IRI>> map, IRI iri, Node n) {
		String value = n.getNodeValue();
		List<IRI> list = new ArrayList<IRI>();
		if(value.contains(delim)) {
			StringTokenizer tokenizer = new StringTokenizer(value, delim);
			while(tokenizer.hasMoreTokens())
				list.add(IRI.create(tokenizer.nextToken()));
		}
		else
			list.add(IRI.create(value));
		
		if(map.containsKey(iri))
			list.addAll(map.get(iri));
		
		map.put(iri, list);
	}
	
	
	/**
	 * Get information requests (e.g., information such as name, id, etc) 
	 * @param infoListNode	Infolist node
	 * @return List of IRIs
	 */
	private List<TreeNode<IRI>> getInfoRequests(Node infoListNode) {
		List<TreeNode<IRI>> inforeqs = new ArrayList<TreeNode<IRI>>();
		List<IRI> infoList = new ArrayList<IRI>();
		NodeList nl = infoListNode.getChildNodes(); // <info>'s
		for(int i = 0; i < nl.getLength(); i++) {
			Node child = nl.item(i);
			boolean required = false;
			IRI iri = null;
			TreeNode<IRI> info = null;
			if(child.hasAttributes()) {
				NamedNodeMap nodemap = child.getAttributes();
				for(int j = 0; j < nodemap.getLength(); j++) {
					Node att = nodemap.item(j);
					if(att.getNodeName().equals("property")) {
						String prop = att.getNodeValue();
						if(doc.getElementById(prop) != null)
							iri = getQuestionIRI(doc.getElementById(prop), child);
						else
							iri = getInfoIRI(prop, child);
						questionNumbering.put(iri, required);
						info = new TreeNode<IRI>(iri);
					}
					else if(att.getNodeName().equals("required"))
						required = Boolean.parseBoolean(att.getNodeValue());
				}
			}
			if(info != null) {
				inforeqs.add(info);
				questionRequired.put(iri, required);
				infoList.add(iri);
			}
		}
		createFormElementList(infoListNode, infoList, null);
		return inforeqs;
	}
	
	
	/**
	 * Get information element IRI
	 * @param propIri	Data property IRI
	 * @param node	Current node
	 * @return IRI of the information element
	 */
	private IRI getInfoIRI(String propIri, Node node) {
		IRI iri = null;
		if(!propIri.equals("")) {
			iri = IRI.create(propIri);
			if(iri != null && verbose)
				System.out.print("\tInfo: " + propIri);
			if(node != null)
				checkQuestionType(iri, node);
			if(iri != null && verbose) 
				System.out.println();
		}
		return iri;
	}
	
	
	/**
	 * Get IRI of question at the given node
	 * @param node	Current node
	 * @param infoEle	Information element node, if applicable
	 * @return IRI of question in given node
	 */
	private IRI getQuestionIRI(Node node, Node infoEle) {
		String iriTxt = node.getTextContent();
		IRI iri = null;
		if(!iriTxt.equals("")) {
			iri = IRI.create(iriTxt);
			if(iri != null && verbose)
				System.out.print("\tQuestion: " + iriTxt);
			if(node.hasAttributes())
				checkQuestionType(iri, node);
			if(node.getParentNode().hasAttributes())
				checkQuestionType(iri, node.getParentNode());
			if(infoEle != null)
				checkQuestionType(iri, infoEle);
			if(iri != null && verbose) 
				System.out.println();
		}
		return iri;
	}
	

	/**
	 * Get the type of question given as an attribute
	 * @param iri	IRI of the question individual
	 * @param curNode	Current node being checked
	 */
	private void checkQuestionType(IRI iri, Node curNode) {
		Node n = curNode.getAttributes().getNamedItem("type");
		if(n != null) {
			ElementType qType = null;
			String type = n.getNodeValue();
			for(int i = 0; i < ElementType.values().length; i++) {
				if(type.equalsIgnoreCase(ElementType.values()[i].toString()))
					qType = ElementType.values()[i];
			}
			if(verbose) System.out.print(" (type: " + qType + ")");
			elementTypes.put(iri, qType);
		}
	}
	
	
	/*	QUESTION TYPES	*/
	
	
	/**
	 * Check if the configuration file contains a question type for the given question
	 * @param i	IRI of the question
	 * @return true if question type is defined in the configuration file, false otherwise
	 */
	public boolean hasDefinedType(IRI i) {
		if(elementTypes.containsKey(i))
			return true;
		else
			return false;
	}
	
	
	/**
	 * Get the question type for the given question IRI
	 * @param i	IRI of individual representing a question
	 * @return Question type
	 */
	public ElementType getQuestionType(IRI i) {
		return elementTypes.get(i);
	}
	
	
	/**
	 * Get the map of question IRIs to their respective type as defined in the configuration file
	 * @return Map of IRIs to question types
	 */
	public Map<IRI,ElementType> getQuestionTypes() {
		return elementTypes;
	}
	
	
	/*	SECTION TYPES	*/
	
	/**
	 * Get the section type for the given section IRI
	 * @param i	Section IRI
	 * @return Section type
	 */
	public SectionType getSectionType(IRI i) {
		return sectionTypes.get(i);
	}
	
	
	/**
	 * Get the map of section IRIs to their respective type as defined in the configuration file
	 * @return Map of IRIs to section types
	 */
	public Map<IRI,SectionType> getSectionTypes() {
		return sectionTypes;
	}
	
	
	/*	SECTION - QUESTION - NUMBERING MAP	*/
	
	
	/**
	 * Get the map of sections and their corresponding questions specified in the configuration file
	 * @return Map of sections' IRIs to the questions they contain 
	 */
	public Map<IRI,List<TreeNode<IRI>>> getSectionMap() {
		return sections;
	}
	
	
	/**
	 * Get the map of sections and whether they (and their components) should or should not be numbered
	 * @return Map of sections' IRIs to whether they are numbered or not
	 */
	public Map<IRI,Boolean> getSectionNumberingMap() {
		return sectionNumbering;
	}
	
	
	/**
	 * Get the map of questions and whether they should or should not be numbered
	 * @return Map of questions' IRIs to whether they are numbered or not
	 */
	public Map<IRI,Boolean> getQuestionNumberingMap() {
		return questionNumbering;
	}
	
	
	/**
	 * Get the map of question IRIs to whether they are required input
	 * @return Map of questions' IRIs to whether they are required or not
	 */
	public Map<IRI,Boolean> getQuestionRequiredMap() {
		return questionRequired;
	}
	
	
	/*	INPUT AND OUTPUT	*/
	
	
	/**
	 * Get file path to main ontology input
	 * @return File path
	 */
	public String getInputOntologyPath() {
		return ontPath;
	}
	
	
	/**
	 * Get the map of imported ontologies' IRIs and file paths
	 * @return Map of IRIs and file paths of imported ontologies
	 */
	public Map<IRI,String> getInputImportsMap() {
		return imports;
	}
	
	
	/**
	 * Get the file path of the output file
	 * @return String with the file path of the output file
	 */
	public String getOutputFilePath() {
		return outPath;
	}

	
	/**
	 * Get the title of the (HTML) output file
	 * @return String describing the title of the output file
	 */
	public String getOutputFileTitle() {
		return title;
	}
	
	
	/**
	 * Get the CSS style class to be used in the form output
	 * @return String specifying the output CSS class
	 */
	public String getCSSStyleClass() {
		return cssStyle;
	}

	
	/*	CLASS BINDINGS	*/
	
	
	/**
	 * Get the OWL class IRI which represents the output type, i.e., all answers will be instances of this class 
	 * @return OWL class IRI
	 */
	public IRI getOutputClass() {
		return getBinding("data", "class");
	}	
	
	
	/**
	 * Get the IRI of the OWL class for data element values 
	 * @return OWL class IRI
	 */
	public IRI getDataElementValueClassBinding() {
		return getBinding("data_element", "class");
	}
	
	
	/**
	 * Get the IRI of the OWL class for form data
	 * @return OWL class IRI
	 */
	public IRI getFormDataClassBinding() {
		return getBinding("form_data", "class");
	}
	
	
	/**
	 * Get the IRI of the OWL class bound to question sections. Each question data will be an instance of this class
	 * @return OWL class IRI
	 */
	public IRI getQuestionSectionClassBinding() {
		return getBinding("question_section", "class");
	}
	
	
	/**
	 * Get the IRI of the OWL class bound to the initial section(s). The data collected in an "initial section" will form an instance of this class
	 * @return OWL class IRI
	 */
	public IRI getInitialSectionClassBinding() {
		return getBinding("patient_section", "class");
	}
	
	
	/**
	 * Get the IRI of the OWL class bound to the final section(s). The data collected in a "final section" will form an instance of this class
	 * @return OWL class IRI
	 */
	public IRI getFinalSectionClassBinding() {
		return getBinding("physician_section", "class");
	}
	
	
	/**
	 * Get the IRI of the OWL class that represents the physician certification
	 * @return OWL class IRI
	 */
	public IRI getPhysicianCertificationClassBinding() {
		return getBinding("physician_cert", "class");
	}
	
	
	/*	PROPERTY BINDINGS	*/
	
	
	/**
	 * Get the property IRI which represents the questions' text
	 * @return OWL data property IRI
	 */
	public IRI getQuestionTextPropertyBinding() {
		return getBinding("questiontext", "property");
	}
	

	/**
	 * Get the property IRI which represents the questions' focus
	 * @return OWL data property IRI
	 */
	public IRI getQuestionFocusPropertyBinding() {
		return getBinding("questionfocus", "property");
	}
	
	
	/**
	 * Get the property IRI which represents the questions' possible value(s)
	 * @return OWL object property IRI
	 */
	public IRI getQuestionValuePropertyBinding() {
		return getBinding("questionvalue", "property");
	}
	
	
	/**
	 * Get the data property IRI which is used to represent the numeric value of a question's option
	 * @return OWL data property IRI
	 */
	public IRI getQuestionDataValuePropertyBinding() {
		return getBinding("questiondatavalue", "property");
	}
	
	
	/**
	 * Get the OWL data property IRI that gives a heading value to a section instance
	 * @return OWL data property IRI
	 */
	public IRI getSectionHeaderPropertyBinding() {
		return getBinding("sectionheading", "property");
	}
	
	
	/**
	 * Get the OWL data property IRI that is used to specify the text at the beginning of a section
	 * @return OWL data property IRI
	 */
	public IRI getSectionTextPropertyBinding() {
		return getBinding("sectiontext", "property");
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasAnswer'
	 * @return OWL object property IRI
	 */
	public IRI getHasAnswerPropertyBinding() {
		return getBinding("has_answer", "property");
	}
	
	
	/**
	 * Get the OWL object property IRI for 'isAnswerTo'
	 * @return OWL object property IRI
	 */
	public IRI getIsAnswerToPropertyBinding() {
		return getBinding("is_answer_to", "property");
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasForm'
	 * @return OWL object property IRI
	 */
	public IRI getHasFormPropertyBinding() {
		return getBinding("has_form", "property");
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasComponent'
	 * @return OWL object property IRI
	 */
	public IRI getHasComponentPropertyBinding() {
		return getBinding("has_component", "property");
	}
	
	
	/**
	 * Get the OWL object property IRI for 'isComponentOf'
	 * @return OWL object property IRI
	 */
	public IRI getIsComponentOfPropertyBinding() {
		return getBinding("is_component_of", "property");
	}
	
	
	/**
	 * Get the OWL object property IRI for 'hasDate'
	 * @return OWL object property IRI
	 */
	public IRI getHasDatePropertyBinding() {
		return getBinding("has_date", "property");
	}
	
	
	/**
	 * Get IRI for a given binding 
	 * @param binding	Name of binding
	 * @param label	Label (e.g., property, class, individual,...)
	 * @return IRI
	 */
	public IRI getBinding(String binding, String label) {
		if(doc.getElementById(binding) == null) 
			throw new MissingConfigurationElementException("Configuration binding for " + label + " '" + binding + "' is missing");
		return IRI.create(doc.getElementById(binding).getTextContent());
	}
	
	
	/*	QUESTION INPUT TYPES (HTML FORM)	*/
	
	
	/**
	 * Get the OWL class IRI which represents an HTML radio input element
	 * @return OWL class IRI
	 */
	public IRI getRadioInputBinding() {
		return getBinding("radio", "question type");
	}
	
	
	/**
	 * Get the OWL class IRI which represents an HTML text field input element
	 * @return OWL class IRI
	 */
	public IRI getTextInputBinding() {
		return getBinding("textarea", "question type");
	}
	
	
	/**
	 * Get the OWL individual IRI which represents the boolean value for true
	 * @return OWL individual IRI
	 */
	public IRI getBooleanTrueValueBinding() {
		return getBinding("bool_true", "boolean value");
	}
	
	
	/**
	 * Get the OWL individual IRI which represents the boolean value for false
	 * @return OWL individual IRI
	 */
	public IRI getBooleanFalseValueBinding() {
		return getBinding("bool_false", "boolean value");
	}
	
	
	/**
	 * Get the OWL individual IRI which represents the form
	 * @return OWL individual IRI
	 */
	public IRI getFormIndividualIRI() {
		return IRI.create(formIri);
	}
	
	
	/*	PRINT SECTION / QUESTION MAP	*/

	
	/**
	 * Print a given map of sections
	 * @param sections	Map of sections
	 */
	@SuppressWarnings("unused")
	private void print(Map<IRI,List<TreeNode<IRI>>> sections) {
		int counter = 0;
		for(IRI iri : sections.keySet()) {
			System.out.println("Section " + counter + ": " + iri.getShortForm());
			List<TreeNode<IRI>> questions = sections.get(iri);
			print(questions);
			counter++;
		}
	}
	
	
	/**
	 * Print a list of questions
	 * @param questions	List of question treenodes
	 */
	private void print(List<TreeNode<IRI>> questions) {
		for(int i = 0; i < questions.size(); i++) {
			TreeNode<IRI> question = questions.get(i);
			print(question);
		}
	}
	
	
	/**
	 * Print the information in a question treenode
	 * @param treenode	Treenode
	 */
	private void print(TreeNode<IRI> treenode) {
		Iterator<TreeNode<IRI>> iter = treenode.iterator();
		TreeNode<IRI> t = null;
		while(iter.hasNext()) {
			t = iter.next();
			for(int j = 0; j <= t.getLevel(); j++) 
				System.out.print("    ");
			System.out.println("(" + t.getLevel() + ") Question: " + t.data.getShortForm());
		}
	}
	
	
	/*	SUBQUESTION TRIGGERS	*/
	
	
	/**
	 * Get the map of question IRI's to the answer(s) that, when chosen, will cause subquestions to appear
	 * @return Map of question IRI's to answer(s)' IRI's whose choice causes subquestions to appear
	 */
	public Map<IRI,IRI> getSubquestionPositiveTriggers() {
		return subquestionPosTriggers;
	}
	
	
	/**
	 * Get the map of question IRI's to the answer(s) that, when chosen, will cause subquestions to disappear
	 * @return Map of question IRI's to answer(s)' IRI's whose choice causes subquestions to disappear
	 */
	public Map<IRI,IRI> getSubquestionNegativeTriggers() {
		return subquestionNegTriggers;
	}

	
	/*	MISC	*/
	
	
	/**
	 * Get the map of question IRI's to the list which determines the order in which question options will 
	 * be presented in the HTML form
	 * @return Map of question IRI's to a list of strings
	 */
	public Map<IRI,List<String>> getOptionsOrderMap() {
		return optionsOrder;
	}
	

	/**
	 * Get the list of question lists parsed in this configuration
	 * @return List of question list objects
	 */
	public List<FormElementList> getQuestionLists() {
		return elementLists;
	}
}
/***********************************************************************
 * mt4j Copyright (c) 2008 - 2009, C.Ruff, Fraunhofer-Gesellschaft All rights reserved.
 *  
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ***********************************************************************/
package org.mt4j.components.visibleComponents.widgets;

import java.util.ArrayList;
import java.util.Iterator;

import javax.media.opengl.GL;

import org.mt4j.components.TransformSpace;
import org.mt4j.components.clipping.Clip;
import org.mt4j.components.visibleComponents.font.BitmapFont;
import org.mt4j.components.visibleComponents.font.BitmapFontCharacter;
import org.mt4j.components.visibleComponents.font.IFont;
import org.mt4j.components.visibleComponents.font.IFontCharacter;
import org.mt4j.components.visibleComponents.shapes.MTRectangle;
import org.mt4j.components.visibleComponents.widgets.keyboard.ITextInputListener;
import org.mt4j.components.visibleComponents.widgets.keyboard.MTKeyboard;
import org.mt4j.input.inputProcessors.componentProcessors.lassoProcessor.IdragClusterable;
import org.mt4j.util.MT4jSettings;
import org.mt4j.util.MTColor;
import org.mt4j.util.math.Tools3D;
import org.mt4j.util.math.Vector3D;
import org.mt4j.util.math.Vertex;

import processing.core.PApplet;
import processing.core.PGraphics;

/**
 * The Class MTTextArea. This widget allows to display text with a specified font.
 * If the constructor with no fixed text are dimensions is used, the text area will
 * expand itself to fit the text in. 
 * <br>
 * If the constructor with fixed dimensions is used, the text will have word wrapping
 * and be clipped to the specified dimensions.
 * 
 * @author Christopher Ruff
 */
public class MTTextArea extends MTRectangle implements IdragClusterable, ITextInputListener, Comparable<Object>{
		
	/** The pa. */
	private PApplet pa;
	
	/** The character list. */
	private ArrayList<IFontCharacter> characterList;
	
	/** The font. */
	private IFont font;
	
	/** The font b box height. */
	private int fontHeight;
	
	/** The show caret. */
	private boolean showCaret;
	
	/** The show caret time. */
	private long showCaretTime = 1500; //ms
	
	/** The caret time counter. */
	private int caretTimeCounter = 0;
	
	/** The enable caret. */
	private boolean enableCaret;
	
	/** The caret width. */
	private float caretWidth;

	private float innerPaddingTop;
	private float innerPaddingLeft;
	
	private float totalScrollTextX;
	private float totalScrollTextY;
	
	//TODO set font color on the fly
	//TODO different font sizes in one textarea?
	//TODO (create mode : expand vertically but do word wrap horizontally?)
	
	private static final int MODE_EXPAND = 0;
	private static final int MODE_WRAP = 1;
	
	private int mode;
	
	private static ArtificalLineBreak artificialLineBreak;
	
	
	/**
	 * Instantiates a new mT text area. 
	 * This constructor creates a textarea with fixed dimensions. 
	 * If the text exceeds the dimensions the text is clipped.
	 * 
	 * @param x the x
	 * @param y the y
	 * @param width the width
	 * @param height the height
	 * @param font the font
	 * @param pApplet the applet
	 */
	public MTTextArea(float x, float y, float width, float height,IFont font, PApplet pApplet) {
		super(	0, -1 * font.getFontMaxAscent(), 	//upper left corner
				width, 	//width
				height,  //height
				pApplet);
		
		init(pApplet, font, MODE_WRAP);
		
		//Position textarea at x,y
		PositionAnchor prevAnchor = this.getAnchor();
		this.setAnchor(PositionAnchor.UPPER_LEFT);
		this.setPositionGlobal(new Vector3D(x,y,0));
		this.setAnchor(prevAnchor);
	}
	
		
	/**
	 * Instantiates a new text area. This constructor creates
	 * a text area with variable dimensions that expands itself when text is added.
	 * 
	 * @param pApplet the applet
	 * @param font the font
	 */
	public MTTextArea(PApplet pApplet, IFont font) {
		super(	0, -1 * font.getFontMaxAscent(), 	//upper left corner
				0, 	//width
				0,  //height
				pApplet);
		
		init(pApplet, font, MODE_EXPAND);
		
		//Position textarea at 0,0
		PositionAnchor prevAnchor = this.getAnchor();
		this.setAnchor(PositionAnchor.UPPER_LEFT);
		this.setPositionGlobal(Vector3D.ZERO_VECTOR);
		this.setAnchor(prevAnchor);
		
		//Expand vertically at enter 
		this.setHeightLocal(this.getTotalLinesHeight());
		this.setWidthLocal(getMaxLineWidth());
	}
	
	
	
	private void init(PApplet pApplet, IFont font, int mode){
		this.pa = pApplet;
		this.font = font;
		
		this.mode = mode;
		switch (this.mode) {
		case MODE_EXPAND:
			//We dont have to clip since we expand the area
			break;
		case MODE_WRAP:
			if (MT4jSettings.getInstance().isOpenGlMode()){ 
				//Clip the text to the area
				this.setClip(new Clip(pApplet, this.getVerticesLocal()[0].x, this.getVerticesLocal()[0].y, this.getWidthXY(TransformSpace.LOCAL), this.getHeightXY(TransformSpace.LOCAL)));
			}
			break;
		default:
			break;
		}
		
		characterList = new ArrayList<IFontCharacter>();
		
		if (MT4jSettings.getInstance().isOpenGlMode())
			this.setUseDirectGL(true);
		
		fontHeight = font.getFontAbsoluteHeight();
		
		caretWidth = 0; 
		innerPaddingTop = 5f;
		innerPaddingLeft = 8f;
		
		showCaret 	= false;
		enableCaret = false;
		showCaretTime = 1000;
		
		this.setStrokeWeight(1.5f);
		this.setStrokeColor(new MTColor(255, 255, 255, 255));
		this.setDrawSmooth(true);
		
		//Draw this component and its children above 
		//everything previously drawn and avoid z-fighting
//		this.setDepthBufferDisabled(true);
		
		this.totalScrollTextX = 0.0f;
		this.totalScrollTextY = 0.0f;
		
		if (artificialLineBreak == null){
			artificialLineBreak = new ArtificalLineBreak();
		}
	}
	
	

	@Override
	public void updateComponent(long timeDelta) {
		super.updateComponent(timeDelta);
		if (enableCaret){
			caretTimeCounter+=timeDelta;
			if (caretTimeCounter >= showCaretTime && !showCaret){
				showCaret 		 = true;
				caretTimeCounter = 0;
			}else if (caretTimeCounter >= showCaretTime && showCaret){
				showCaret 		 = false;
				caretTimeCounter = 0;
			}
		}
	}
	
	
	@Override
	public void preDraw(PGraphics graphics) {
		super.preDraw(graphics);
		
		//Hack for drawing anti aliased stroke outline over the clipped area
		noStrokeSettingSaved = this.isNoStroke();
		if (this.mode == MODE_WRAP && this.getClip() != null && !this.isNoStroke()){
			this.setNoStroke(true);	
		}
	}
	
	
	@Override
	public void drawComponent(PGraphics g) {
		super.drawComponent(g);
		
		//Add caret if its time 
		if (enableCaret && showCaret){
			characterList.add(this.getFont().getFontCharacterByUnicode("|"));
		}
		
		int charListSize = characterList.size();
		
		float thisLineTotalXAdvancement = 0;
		float lastXAdvancement = innerPaddingLeft;

		//Account for TOP inner padding if using WRAP mode -> translate text
		switch (this.mode) {
		case MODE_EXPAND:
			//Dont need to translate for innerpadding TOP because we do that in setHeight() making the whole textarea bigger
			break;
		case MODE_WRAP:
			//Need to translate innerpadding TOP because we shouldnt make the textarea bigger like in expand mode
			g.pushMatrix();
			g.translate(0, innerPaddingTop);
			break;
		default:
			break;
		}
		
//		/*//
		//To set caret at most left start pos when charlist empty (looks better)
		if (enableCaret && showCaret && charListSize == 1){
			lastXAdvancement = 0;
		}
//		*/
		
		if (this.isUseDirectGL()){
			GL gl = Tools3D.beginGL(pa);
			gl.glPushMatrix(); //FIXME TEST text scrolling 
			gl.glTranslatef(totalScrollTextX, totalScrollTextY, 0);
			
			for (int i = 0; i < charListSize; i++) {
				IFontCharacter character = characterList.get(i);
				//Step to the right by the amount of the last characters x advancement
				gl.glTranslatef(lastXAdvancement, 0, 0);
				//Save total amount gone to the right in this line 
				thisLineTotalXAdvancement += lastXAdvancement;
				lastXAdvancement = 0;

				//Draw the letter
				character.drawComponent(gl);

				//Check if newLine occurs, goto start at new line
				if (character.getUnicode().equals("\n")){
					gl.glTranslatef(-thisLineTotalXAdvancement, fontHeight, 0);
					thisLineTotalXAdvancement = 0;
					lastXAdvancement = innerPaddingLeft;
				}else{
					//If caret is showing and we are at index one before caret calc the advancement to include the caret in the text area
					if (enableCaret && showCaret && i == charListSize-2){
						if (character.getUnicode().equals("\t")){
							lastXAdvancement = character.getHorizontalDist() - character.getHorizontalDist() / 20;
						}else{
							//approximated value, cant get the real one
							lastXAdvancement = 2 + character.getHorizontalDist() - (character.getHorizontalDist() / 3);
						}
					}else{
						lastXAdvancement = character.getHorizontalDist();
					}
				}
			}
			
			gl.glPopMatrix(); //FIXME TEST text scrolling - but IMHO better done with parent list/scroll container
			
			Tools3D.endGL(pa);
		}
		else{ //P3D rendering
			g.pushMatrix(); //FIXME TEST text scrolling - but IMHO better done with parent list/scroll container
			g.translate(totalScrollTextX, totalScrollTextY, 0);
			
			for (int i = 0; i < charListSize; i++) {
				IFontCharacter character = characterList.get(i);
				//Step to the right by the amount of the last characters x advancement
				pa.translate(lastXAdvancement, 0, 0); //original
				//Save total amount gone to the right in this line
				thisLineTotalXAdvancement += lastXAdvancement;
				lastXAdvancement = 0;
				
				//Draw the letter
				character.drawComponent(g);
				
				//Check if newLine occurs, goto start at new line
				if (character.getUnicode().equals("\n")){
					pa.translate(-thisLineTotalXAdvancement, fontHeight, 0);
					thisLineTotalXAdvancement = 0;
					lastXAdvancement = innerPaddingLeft;
				}else{
					//If caret is showing and we are at index one before caret calc the advancement
					if (enableCaret && showCaret && i == charListSize - 2){
						if (character.getUnicode().equals("\t")){
							lastXAdvancement = character.getHorizontalDist() - character.getHorizontalDist( ) / 20;
						}else{
							//approximated value, cant get the real one
							lastXAdvancement = 2 + character.getHorizontalDist() - (character.getHorizontalDist() / 3);
						}
					}else{
						lastXAdvancement = character.getHorizontalDist();
					}
				}
			}
			g.popMatrix();//FIXME TEST text scrolling - but IMHO better done with parent list/scroll container
		}
		
		//FIXME TEST //Innerpadding TOP for wrapped textarea -> translates the text content downwards
		switch (this.mode) {
		case MODE_EXPAND:
			break;
		case MODE_WRAP:
			//Need to translate innerpadding because we shouldnt make the textarea bigger
			g.popMatrix();
			break;
		default:
			break;
		}
		
		//remove caret
		if (enableCaret && showCaret){
			characterList.remove(charListSize-1);
		}
	}
	
	
	private boolean noStrokeSettingSaved;
	
	@Override
	public void postDraw(PGraphics graphics) {
		super.postDraw(graphics);
		//Hack for drawing anti aliased stroke outline over the clipped area
		if (this.mode == MODE_WRAP && this.getClip()!= null && !noStrokeSettingSaved){
			this.setNoStroke(noStrokeSettingSaved);
			boolean noFillSavedSetting = this.isNoFill();
			this.setNoFill(true);
			super.drawComponent(graphics);//Draw only stroke line after we ended clipping do preserve anti aliasing - hack
			this.setNoFill(noFillSavedSetting);
		}
	}
	
	//FIXME TEST scrolling (used in MTTextField for example)
	protected void scrollTextX(float amount){
		this.totalScrollTextX += amount;
	}
	protected void scrollTextY(float amount){
		this.totalScrollTextY += amount;
	}
	protected float getScrollTextX() {
		return this.totalScrollTextX;
	}
	protected float getScrollTextY() {
		return this.totalScrollTextY;
	}
	
	
	//FIXME TEST ?
	/**
	 * Changes the texture filtering for the textarea's bitmap font.
	 * (if a bitmap font is used).
	 * If the parameter is "true" this will allow the text being scaled without getting
	 * too pixelated. If the text isnt going to be scaled ever, it is best to leave or
	 * set this to "false" for a sharper text.
	 * <br>NOTE: Only applies if OpenGL is the renderer and the textarea uses a bitmap font.
	 * <br>NOTE: This affects the whole bitmap font so if it is used elsewhere it is changed 
	 * there, too.
	 * 
	 * @param scalable the new bitmap font scalable
	 */
	public void setBitmapFontTextureFiltered(boolean scalable){
		if (MT4jSettings.getInstance().isOpenGlMode() && this.getFont() instanceof BitmapFont){
			BitmapFont font = (BitmapFont)this.getFont();
			IFontCharacter[] characters = font.getCharacters();
			for (int i = 0; i < characters.length; i++) {
				IFontCharacter fontCharacter = characters[i];
				if (fontCharacter instanceof BitmapFontCharacter) {
					BitmapFontCharacter bChar = (BitmapFontCharacter) fontCharacter;
					bChar.setTextureFiltered(scalable);
				}
			}
		}
	}
	
	//FIXME REMOVE!?
//	private boolean filteringDone;
//	private boolean isBitmapFont;
//	
//	@Override
//	public void setMatricesDirty(boolean baseMatrixDirty) {
//		super.setMatricesDirty(baseMatrixDirty);
//		
//		if (isBitmapFont && !filteringDone){
////			filteringDone = true;
//			
//			MTComponent current = this;
//			boolean hasScale;
//			do {
//				Matrix local = current.getLocalMatrix();
//				current = current.getParent();
//			} while (current != null);
//			while (current.getParent() != null) {
//				
//				
//			}
//			
//			//TODO change fonts' filtering from NEAREST to LINEAR once after scaling is done
//		}
//	}
//	
//	private boolean checkForScaling(){
//		return checkForScalingRecursive(this);
//	}
//	
//	private MTComponent checkForScalingRecursive(MTComponent current){
//		//System.out.println("Processing: " + current.getName());
//		if (current.getParent() != null){
//			
//		}
//	}
	
	
	/**
	 * Sets the width local.
	 * 
	 * @param width the new width local
	 */
	@Override
	public void setWidthLocal(float width){
		super.setWidthLocal(width);
//				Vertex[] v = this.getVerticesLocal();
//				MTColor c = this.getFillColor();
//				this.setVertices(
//						new Vertex[]{
//								v[0], 
//								new Vertex(width, v[1].getY(), v[1].getZ(), c.getR(), c.getG(), c.getB(), c.getAlpha()), 
//								new Vertex(width, v[2].getY(), v[2].getZ(), c.getR(), c.getG(), c.getB(), c.getAlpha()), 
//								v[3], 
//								v[4]});

		switch (this.mode) {
		case MODE_EXPAND:
			
			break;
		case MODE_WRAP:
			//if in MODE_WRAP also reset the size of the CLIP SHAPE!
			if (MT4jSettings.getInstance().isOpenGlMode() && this.getClip() != null && this.getClip().getClipShape() instanceof MTRectangle){ 
				MTRectangle clipRect = (MTRectangle)this.getClip().getClipShape();
				//				clipRect.setWidthLocal(this.getWidthXY(TransformSpace.LOCAL));
				//Clip the text to the area
				//				this.setClip(new Clip(pApplet, this.getVerticesLocal()[0].x, this.getVerticesLocal()[0].y, this.getWidthXY(TransformSpace.LOCAL), this.getHeightXY(TransformSpace.LOCAL)));
				//				clipRect.setVertices(Vertex.getDeepVertexArrayCopy(this.getVerticesLocal()));
				clipRect.setVertices(this.getVerticesLocal());
			}
			this.updateLayout();
			break;
		default:
			break;
		}
	}
	/**
	 * Sets the height local.
	 * 
	 * @param height the new height local
	 */
	@Override
	public void setHeightLocal(float height){ 
		Vertex[] v = this.getVerticesLocal();
//		this.setVertices(
//				new Vertex[]{
//						new Vertex(v[2].getX(), upperLeftLocal.y + height, v[2].getZ(), this.getFillRed(), this.getFillGreen(), this.getFillBlue(), this.getFillAlpha()),
//						v[0], 
//						v[1] , 
//						new Vertex(v[2].getX(), upperLeftLocal.y + height, v[2].getZ(), this.getFillRed(), this.getFillGreen(), this.getFillBlue(), this.getFillAlpha()),
//						new Vertex(v[3].getX(), upperLeftLocal.y + height, v[3].getZ(), this.getFillRed(), this.getFillGreen(), this.getFillBlue(), this.getFillAlpha()),
//						v[4]});
		
		switch (this.mode) {
		case MODE_EXPAND:
			this.setVertices(new Vertex[]{
					new Vertex(v[0].x,	-font.getFontMaxAscent() - innerPaddingTop, 		v[0].z, v[0].getTexCoordU(), v[0].getTexCoordV(), v[0].getR(), v[0].getG(), v[0].getB(), v[0].getA()), 
					new Vertex(v[1].x, 	-font.getFontMaxAscent() - innerPaddingTop, 		v[1].z, v[1].getTexCoordU(), v[1].getTexCoordV(), v[1].getR(), v[1].getG(), v[1].getB(), v[1].getA()), 
					new Vertex(v[2].x, 	-font.getFontMaxAscent() - innerPaddingTop + height + (2 * innerPaddingTop), 	v[2].z, v[2].getTexCoordU(), v[2].getTexCoordV(), v[2].getR(), v[2].getG(), v[2].getB(), v[2].getA()), 
					new Vertex(v[3].x,	-font.getFontMaxAscent() - innerPaddingTop + height + (2 * innerPaddingTop),	v[3].z, v[3].getTexCoordU(), v[3].getTexCoordV(), v[3].getR(), v[3].getG(), v[3].getB(), v[3].getA()), 
					new Vertex(v[4].x,	-font.getFontMaxAscent() - innerPaddingTop,			v[4].z, v[4].getTexCoordU(), v[4].getTexCoordV(), v[4].getR(), v[4].getG(), v[4].getB(), v[4].getA()), 
			});
			break;
		case MODE_WRAP:
			super.setHeightLocal(height);
			//if in MODE_WRAP also reset the size of the CLIP SHAPE!
			if (MT4jSettings.getInstance().isOpenGlMode() && this.getClip() != null && this.getClip().getClipShape() instanceof MTRectangle){ 
				MTRectangle clipRect = (MTRectangle)this.getClip().getClipShape();
				//				clipRect.setVertices(Vertex.getDeepVertexArrayCopy(this.getVerticesLocal()));
				clipRect.setVertices(this.getVerticesLocal());
			}
			this.updateLayout();
			break;
		default:
			break;
		}
	}
	
	@Override
	public void setSizeLocal(float width, float height) {
		if (width > 0 && height > 0){
			Vertex[] v = this.getVerticesLocal();
			switch (this.mode) {
			case MODE_EXPAND:
				this.setVertices(new Vertex[]{
						new Vertex(v[0].x,			-font.getFontMaxAscent() - innerPaddingTop, 		v[0].z, v[0].getTexCoordU(), v[0].getTexCoordV(), v[0].getR(), v[0].getG(), v[0].getB(), v[0].getA()), 
						new Vertex(v[0].x+width, 	-font.getFontMaxAscent() - innerPaddingTop, 		v[1].z, v[1].getTexCoordU(), v[1].getTexCoordV(), v[1].getR(), v[1].getG(), v[1].getB(), v[1].getA()), 
						new Vertex(v[0].x+width, 	-font.getFontMaxAscent() - innerPaddingTop + height + (2 * innerPaddingTop), v[2].getTexCoordV(), v[2].getR(), v[2].getG(), v[2].getB(), v[2].getA()), 
						new Vertex(v[3].x,			-font.getFontMaxAscent() - innerPaddingTop + height + (2 * innerPaddingTop),	v[3].z, v[3].getTexCoordU(), v[3].getTexCoordV(), v[3].getR(), v[3].getG(), v[3].getB(), v[3].getA()), 
						new Vertex(v[4].x,			-font.getFontMaxAscent() - innerPaddingTop,			v[4].z, v[4].getTexCoordU(), v[4].getTexCoordV(), v[4].getR(), v[4].getG(), v[4].getB(), v[4].getA()), 
				});
				break;
			case MODE_WRAP:
				super.setSizeLocal(width, height);
				//if in MODE_WRAP also reset the size of the CLIP SHAPE!
				if (MT4jSettings.getInstance().isOpenGlMode() && this.getClip() != null && this.getClip().getClipShape() instanceof MTRectangle){ 
					MTRectangle clipRect = (MTRectangle)this.getClip().getClipShape();
					//clipRect.setVertices(Vertex.getDeepVertexArrayCopy(this.getVerticesLocal()));
					clipRect.setVertices(this.getVerticesLocal());
				}
				this.updateLayout();
				break;
			default:
				break;
			}
		}
	}
	
	
	/**
	 * Appends the string to the textarea.
	 * 
	 * @param string the string
	 */
	synchronized public void appendText(String string){
		for (int i = 0; i < string.length(); i++) {
			appendCharByUnicode(string.substring(i, i+1));
		}
	}
	
	/**
	 * Sets the provided string as the text of this textarea.
	 * 
	 * @param string the string
	 */
	synchronized public void setText(String string){
		clear();
		for (int i = 0; i < string.length(); i++) {
			appendCharByUnicode(string.substring(i, i+1));
		}
	}
	
	
	/* (non-Javadoc)
	 * @see org.mt4j.components.visibleComponents.widgets.keyboard.ITextInputListener#getText()
	 */
	public String getText(){
		String returnString = "";
		for (Iterator<IFontCharacter> iter = this.characterList.iterator(); iter.hasNext();) {
			IFontCharacter character = (IFontCharacter) iter.next();
			String unicode = character.getUnicode();
//			if (unicode.equalsIgnoreCase("tab")){ //TODO why was this here? tab is handled with "\t" !
//				returnString += "    ";
//			}
//			else if (unicode.equalsIgnoreCase("\n")){
//				returnString += " ";
//			}
//			else{
//				returnString += unicode;
//			}
			if (!character.equals(MTTextArea.artificialLineBreak)){
				returnString += unicode;
			}
		}
		return returnString;
	}
	
	
	/**
	 * Append char by name.
	 * @param characterName the character name
	 */
	synchronized public void appendCharByName(String characterName){
		//Get the character from the font
		IFontCharacter character = font.getFontCharacterByName(characterName);
		if (character == null){
			System.err.println("Error adding character with name '" + characterName + "' to the textarea. The font couldnt find the character. -> Trying to use 'missing glyph'");
			character = font.getFontCharacterByName("missing-glyph");
			if (character != null)
				addCharacter(character);
		}else{
			addCharacter(character);
		}
	}
	
	
	
	/* (non-Javadoc)
	 * @see org.mt4j.components.visibleComponents.widgets.keyboard.ITextInputListener#appendCharByUnicode(java.lang.String)
	 */
	synchronized public void appendCharByUnicode(String unicode){
		//Get the character from the font
		IFontCharacter character = font.getFontCharacterByUnicode(unicode);
		if (character == null){
//			System.err.println("Error adding character with unicode '" + unicode + "' to the textarea. The font couldnt find the character. ->Trying to use 'missing glyph'");
			character = font.getFontCharacterByUnicode("missing-glyph");
			if (character != null)
				addCharacter(character);
		}else{
			addCharacter(character);
		}
	}
	
	
	/**
	 * Gets the characters. Also returns articifially added new line characters that were
	 * added by the MTTextArea
	 * @return the characters
	 */
	public IFontCharacter[] getCharacters(){
		return this.characterList.toArray(new IFontCharacter[this.characterList.size()]);
	}
	
	
	/**
	 * Adds the character.
	 * 
	 * @param character the character
	 */
	private void addCharacter(IFontCharacter character){
		this.characterList.add(character);
		
		this.characterAdded(character);
	}
	
	/**
	 * Invoked everytime a character is added.
	 *
	 * @param character the character
	 */
	protected void characterAdded(IFontCharacter character){
		switch (this.mode) {
		case MODE_EXPAND:
			if (character.getUnicode().equals("\n")){
				//Expand vertically at enter 
				this.setHeightLocal(this.getTotalLinesHeight());
				//TODO make behaviour settable
				//Moves the Textarea up at a enter character instead of down 
				this.translate(new Vector3D(0, -fontHeight, 0));
			}else{
				//Expand the textbox to the extend of the widest line width
				this.setWidthLocal(getMaxLineWidth());
			}
			break;
		case MODE_WRAP:
			float localWidth = this.getWidthXY(TransformSpace.LOCAL);
//			float maxLineWidth = this.getMaxLineWidth(); 
			float maxLineWidth = this.getLastLineWidth();
			
			if (this.characterList.size() > 0 && maxLineWidth > localWidth ) {
//			if (this.characterList.size() > 0 && maxLineWidth > (localWidth - 2 * this.getInnerPaddingLeft())) {
//				this.characterList.add(this.characterList.size() -1 , this.font.getFontCharacterByUnicode("\n"));
				try {
					int lastSpacePos = getLastWhiteSpace();
					if (lastSpacePos != -1 ){ //&& !this.characterList.get(characterList.size()-1).getUnicode().equals("\n")
//						this.characterList.add(lastSpacePos + 1, this.font.getFontCharacterByUnicode("\n"));
						this.characterList.add(lastSpacePos + 1, MTTextArea.artificialLineBreak);
					}else{
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			break;
		default:
			break;
		}
	}
	
	
	private int getLastWhiteSpace(){
		for (int i = this.characterList.size()-1; i > 0; i--) {
			IFontCharacter character = this.characterList.get(i);
			if (character.getUnicode().equals(" ")){
				return i;
			}else if (character.getUnicode().equals("\n")){// stop search when newline found before first whitespace
				return -1;
			}
		}
		return -1;
	}
	
	
	/**
	 * When Character removed.
	 *
	 * @param character the character
	 */
	protected void characterRemoved(IFontCharacter character){
		switch (this.mode) {
		case MODE_EXPAND:
			//Resize text field
			if (character.getUnicode().equals("\n")){
				//Reduce field vertically at enter
				this.setHeightLocal(this.getTotalLinesHeight());
				//makes the textarea go down when a line is removed instead staying at the same loc.
				this.translate(new Vector3D(0, fontHeight, 0));
			}else{
				//Reduce field horizontally
				this.setWidthLocal(getMaxLineWidth());
			}
			break;
		case MODE_WRAP:
			
			break;
		default:
			break;
		}
	}
	
	/**
	 * Removes the last character in the textarea.
	 */
	synchronized public void removeLastCharacter(){
		if (this.characterList.isEmpty())
			return;
		
		//REMOVE THE CHARACTER
		IFontCharacter lastCharacter = this.characterList.get(this.characterList.size()-1);
		this.characterList.remove(this.characterList.size()-1);
		
		this.characterRemoved(lastCharacter);
	}
	
	
	/**
	 * resets the textarea, clears all characters.
	 */
	public void clear(){
		while (!characterList.isEmpty()){
			removeLastCharacter();
		}
	}
	
	
	/**
	 * Gets the last line width.
	 *
	 * @return the last line width
	 */
	protected float getLastLineWidth(){
		float currentLineWidth = 2 * this.getInnerPaddingLeft() + caretWidth;
		for (int i = 0; i < this.characterList.size(); i++) {
			IFontCharacter character = this.characterList.get(i);
			if (character.getUnicode().equals("\n")){
				currentLineWidth = 2 * this.getInnerPaddingLeft() + caretWidth;; 
			}else{
				currentLineWidth += character.getHorizontalDist();
			}
		}
		return currentLineWidth;
	}
	
	
	/**
	 * Gets the max line width. The padding is also added.
	 * 
	 * @return the max line width
	 */
	protected float getMaxLineWidth(){
		float currentLineWidth = 2 * this.getInnerPaddingLeft() + caretWidth;
		float maxWidth = currentLineWidth;
		
		for (int i = 0; i < this.characterList.size(); i++) {
			IFontCharacter character = this.characterList.get(i);
			
			if (character.getUnicode().equals("\n")){
				if (currentLineWidth > maxWidth){
					maxWidth = currentLineWidth;
				}
				currentLineWidth = 2 * this.getInnerPaddingLeft() + caretWidth;
			}else{
				currentLineWidth += character.getHorizontalDist();
				if (currentLineWidth > maxWidth){
					maxWidth = currentLineWidth;
				}
			}
		}
		return maxWidth;
	}

	
	/**
	 * Gets the total lines height. Padding is not included
	 * 
	 * @return the total lines height
	 */
	protected float getTotalLinesHeight(){
		float height = font.getFontAbsoluteHeight() ;//
		for (int i = 0; i < this.characterList.size(); i++) {
			IFontCharacter character = this.characterList.get(i);
			if (character.getUnicode().equals("\n")){
				height += fontHeight;
			}
		}
		return height;
	}
	
	
	public void setInnerPadding(float innerPadding){
		this.setInnerPaddingTop(innerPadding);
		this.setInnerPaddingLeft(innerPadding);
	}

	public float getInnerPaddingTop() {
		return this.innerPaddingTop;
	}

	public void setInnerPaddingTop(float innerPaddingTop) {
		this.innerPaddingTop = innerPaddingTop;
		switch (this.mode) {
		case MODE_EXPAND:
			//At MODE_EXPAND we re-set the text so the size gets re-calculated
			//We can safely do this since in EXPAND mode we didnt add any artificial control characters
			this.updateLayout();
			break;
		case MODE_WRAP:
			//At MODE_WRAP the padding is done with gl_Translate calls so we dont have to reset the size
			//TODO also reset? this.setText(this.getText());?
			break;
		default:
			break;
		}
	}

	public float getInnerPaddingLeft() {
		return this.innerPaddingLeft;
	}

	public void setInnerPaddingLeft(float innerPaddingLeft) {
		this.innerPaddingLeft = innerPaddingLeft;
		switch (this.mode) {
		case MODE_EXPAND:
			//At MODE_EXPAND we re-set the text so the size gets re-calculated
			//We can safely do this since in EXPAND mode we didnt add any artificial control characters
			this.updateLayout();
			break;
		case MODE_WRAP:
			// WE HAVE TO RESET THE ORIGINAL TEXT BECAUSE WE BREAK THE LINE AT DIFFERENT POSITIONS IF THE INNERPADDING IS CHANGED!
			this.updateLayout();
			break;
		default:
			break;
		}
	}
	
	/**
	 * Updates layout. (just does this.setText(this.getText()))
	 */
	protected void updateLayout(){
		this.setText(this.getText());
	}


	/**
	 * Gets the line count.
	 * 
	 * @return the line count
	 */
	public int getLineCount(){
		int count = 0;
		for (int i = 0; i < this.characterList.size(); i++) {
			IFontCharacter character = this.characterList.get(i);
			if (character.getUnicode().equals("\n")){
				count++;
			}
		}
		return count;
	}
	
	
	
	/**
	 * Gets the font.
	 * 
	 * @return the font
	 */
	public IFont getFont() {
		return font;
	}

	/**
	 * Snap to keyboard.
	 * 
	 * @param mtKeyboard the mt keyboard
	 */
	public void snapToKeyboard(MTKeyboard mtKeyboard){
		//OLD WAY
//		this.translate(new Vector3D(30, -(getFont().getFontAbsoluteHeight() * (getLineCount())) + getFont().getFontMaxDescent() - borderHeight, 0));
		mtKeyboard.addChild(this);
		this.setPositionRelativeToParent(new Vector3D(40, -this.getHeightXY(TransformSpace.LOCAL)*0.5f));
	}


	public boolean isSelected() {
		// TODO Auto-generated method stub
		return false;
	}

	public void setSelected(boolean selected) {
		// TODO Auto-generated method stub
	}


	/**
	 * Checks if is enable caret.
	 * 
	 * @return true, if is enable caret
	 */
	public boolean isEnableCaret() {
		return enableCaret;
	}


	/**
	 * Sets the enable caret.
	 * 
	 * @param enableCaret the new enable caret
	 */
	public void setEnableCaret(boolean enableCaret) {
		if (this.getFont().getFontCharacterByUnicode("|") != null){
			this.enableCaret = enableCaret;
			
			if (enableCaret){
				this.caretWidth = 10;
			}else{
				this.caretWidth = 0;
			}
			
			if (this.mode == MODE_EXPAND){
				this.setWidthLocal(this.getMaxLineWidth());
			}
		}else{
			System.err.println("Cant enable caret for this textfield, the font doesent include the letter '|'");
		}
	}


	public int compareTo(Object o) {
		if (o instanceof MTTextArea) {
			MTTextArea ta = (MTTextArea)o;
			return this.getText().compareToIgnoreCase(ta.getText());
		} else {
			return 0;
		}
	}
	
	
	
	/**
	 * Artifical line break to be used instead of the regular line break
	 * to indicate that this linebreak was added by the text area itself for
	 * layout reasons and doesent really belong to the supplied text.
	 * 
	 * @author Christopher Ruff
	 */
	private class ArtificalLineBreak implements IFontCharacter{
		public void drawComponent(PGraphics g) {}
		public void drawComponent(GL gl) {	}
		public int getHorizontalDist() {
			return 0;
		}
		public String getUnicode() {
			return "\n";
		}
	}


}
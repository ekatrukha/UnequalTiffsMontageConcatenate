package unequaltiffs;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.gui.Toolbar;
import ij.util.Java2;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class UTMontage< T extends RealType< T > & NativeType< T > > {

	final ArrayList<long []> im_dims;
	final ArrayList<Img<T>> imgs_in;	
	final ArrayList<IntervalView<T>> intervals;
	int nCols;
	int nRows;
	final boolean bMultiCh;
	int nDimN;
	int [][] indexes; 
	final long[][] singleBox;
	final int nImgN;
	int nAlignMontage;
	
	public UTMontage(final ArrayList<Img<T>> imgs_in_, final ArrayList<long []> im_dims_, final boolean bMultiCh_)
	{
		imgs_in = imgs_in_;
		im_dims = im_dims_;
		bMultiCh = bMultiCh_;
		nDimN = im_dims.get(0).length;
		singleBox = new long[2][nDimN];
		intervals = new ArrayList<IntervalView<T>>();
		nImgN = im_dims.size();
	}
	
	public Img< T > makeMontage(final int nRows_, final int nCols_, final int nAlignMontage_)
	{
		nCols = nCols_;
		nRows = nRows_;
		nAlignMontage = nAlignMontage_;
		
		//calculate the range of boxes size
		indexes = new int[nCols][nRows];
		int nR = 0;
		int nC = 0;
		//for(int i = 0; i<im_dims.size();i++)
		for(int i = 0; i<nCols*nRows;i++)
		{
			if(i<nImgN)
			{
				for(int d=0;d<nDimN;d++)
				{
					if(im_dims.get(i)[d]>singleBox[1][d])
					{
						singleBox[1][d] =  im_dims.get(i)[d];
					}
				}
				indexes[nC][nR] = i;
			}
			else
			{
				//empty cell
				indexes[nC][nR] = -1;
			}
			nC++;
			if(nC >= nCols)
			{
				nC = 0;
				nR++;
			}
		}
		if(nAlignMontage == UnequalTiffs.ALIGN_ZERO)
		{
			for(int i = 0; i<im_dims.size();i++)
			{
				intervals.add(Views.interval(Views.extendZero(imgs_in.get(i)),new FinalInterval(singleBox[0],singleBox[1])));
			}
		}
		else
		{
			long [] nShifts = new long[nDimN];
			for(int i = 0; i<im_dims.size();i++)
			{
				for(int d=0;d<nDimN;d++)
				{
					nShifts[d] = (int) Math.floor(0.5*(singleBox[1][d]-im_dims.get(i)[d]));
				}
				
				intervals.add(Views.interval(
											Views.translate(
													Views.extendZero(imgs_in.get(i))
											,nShifts),
							new FinalInterval(singleBox[0],singleBox[1])));
			}
		}
		
		final long[] dimensions = new long[nDimN];
		dimensions[0] = nCols*singleBox[1][0];
		dimensions[1] = nRows*singleBox[1][1];
		if(nDimN>2)
		{
			for(int d=2;d<nDimN;d++)
			{
				dimensions[d]=singleBox[1][d];
			}
		}
		final int[] cellDimensions;
		if(bMultiCh)
		{
			cellDimensions = new int[] { (int)singleBox[1][0], (int)singleBox[1][1], (int)singleBox[1][2] };
		}
		else
		{			
			cellDimensions = new int[] { (int)singleBox[1][0], (int)singleBox[1][1], 1 };
		}
		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{
				final int x = ( int ) cell.min( 0 );
				final int y = ( int ) cell.min( 1 );
				final int nCol = Math.round(x/singleBox[1][0]);
				final int nRow = Math.round(y/singleBox[1][1]);				
				final int imInd = indexes[nCol][nRow];
				//empty cell
				if(imInd<0)
					return;
				final Cursor<T> curCell = cell.localizingCursor();
				final RandomAccess<T> ra = intervals.get(imInd).randomAccess();

				long [] pos = new long [nDimN];
				while(curCell.hasNext())
				{
					curCell.fwd();
					curCell.localize(pos);
					pos[0]-=x;
					pos[1]-=y;
					ra.setPosition(pos);
					curCell.get().set(ra.get());
				}
			}
		
		};
		
		Cursor<T> cursorTest = imgs_in.get(0).cursor();
		cursorTest.fwd();
		return new ReadOnlyCachedCellImgFactory().create(
				dimensions,
				cursorTest.get(),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions ).cacheType(CacheType.BOUNDED) );
		
	}
	/** function adding filenames to overlay of Montage **/
	public void addCaptionsOverlay(final String[] filenames, final ImagePlus im)
	{
		
		Overlay imOverlay = new Overlay(); 
		final Font font = new Font(TextRoi.getDefaultFontName(),TextRoi.getDefaultFontStyle(),TextRoi.getDefaultFontSize());

		final Graphics g = (new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)).createGraphics();
		g.setFont(font);
		final FontMetrics fM = g.getFontMetrics();
		
		TextRoi txtROI;
		Rectangle2D.Double bounds;
		int nR = 0;
		int nC = 0;
		//for(int i = 0; i<im_dims.size();i++)
		for(int i = 0; i<nImgN;i++)
		{
			String in = getTruncatedString( fM, g, (int) singleBox[1][0]-5, filenames[i]);
			txtROI = new TextRoi(5+nC*singleBox[1][0], 5+nR*singleBox[1][1], getTruncatedString( fM, g, (int) singleBox[1][0]-5, filenames[i] ), font);
			txtROI.setStrokeColor(Toolbar.getForegroundColor());
			txtROI.setAntiAlias(TextRoi.isAntialiased());
			txtROI.setJustification(TextRoi.getGlobalJustification());
			bounds = txtROI.getFloatBounds();
			bounds.width = 5;
			txtROI.setBounds(bounds);
			
//			if(bounds.width>singleBox[1][0])
//			{
//				bounds.width = (double)singleBox[1][0];
//				txtROI.setBounds(bounds);
//			}
			
			
			imOverlay.add(txtROI);
			
			nC++;
			if(nC >= nCols)
			{
				nC = 0;
				nR++;
			}
		}
		
		im.setOverlay(imOverlay);
		
	}
	
	String getTruncatedString(final FontMetrics fM,  final Graphics g, final int nMaxWidth, final String sIn)
	{
		if (sIn == null)
            return null;
		
		String truncated = sIn;
		int length = sIn.length();
		while (length>0)
		{
			if(fM.getStringBounds(truncated, g).getWidth()<=nMaxWidth)
			{
				return truncated;
			}
			length--;
			truncated = sIn.substring(0, length);
		}
		return "";
	}
	
}

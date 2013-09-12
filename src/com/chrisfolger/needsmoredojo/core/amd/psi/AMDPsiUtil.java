package com.chrisfolger.needsmoredojo.core.amd.psi;

import com.chrisfolger.needsmoredojo.core.amd.AMDImport;
import com.chrisfolger.needsmoredojo.core.amd.define.DefineResolver;
import com.intellij.lang.javascript.psi.JSArrayLiteralExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSParameter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AMDPsiUtil
{
    public enum Direction
    {
        UP,
        DOWN
    }

    public static PsiElement getDefineForVariable(PsiFile file, String textToCompare)
    {
        List<PsiElement> defines = new ArrayList<PsiElement>();
        List<PsiElement> parameters = new ArrayList<PsiElement>();
        new DefineResolver().gatherDefineAndParameters(file, defines, parameters);

        for(int i=0;i<parameters.size();i++)
        {
            if(i > defines.size() - 1)
            {
                return null; // amd import is being modified
            }

            if(parameters.get(i).getText().equals(textToCompare))
            {
                return defines.get(i);
            }
        }

        return null;
    }

    public static PsiElement getNearestComma(PsiElement start)
    {
        PsiElement sibling = start.getPrevSibling();
        while(sibling != null && !(sibling instanceof JSLiteralExpression) && !(sibling instanceof JSParameter))
        {
            if(sibling.getText().equals(","))
            {
                return sibling;
            }

            sibling = sibling.getPrevSibling();
        }

        return null;
    }

    public static JSLiteralExpression getNearestLiteralExpression(PsiElement element, Direction direction)
    {
        PsiElement node = element;
        if(direction == Direction.UP)
        {
            node = element.getPrevSibling();
        }
        else
        {
            node = element.getNextSibling();
        }

        int tries = 0;
        while(tries < 5)
        {
            if(node instanceof  JSLiteralExpression)
            {
                return (JSLiteralExpression) node;
            }

            if(node == null)
            {
                return null;
            }

            if(direction == Direction.UP)
            {
                node = node.getPrevSibling();
            }
            else
            {
                node = node.getNextSibling();
            }

            tries ++;
        }

        return null;
    }

    public static void removeTrailingCommas(Set<PsiElement> deleteList, JSArrayLiteralExpression literal, PsiElement function)
    {
        try
        {
            PsiElement trailingComma = AMDPsiUtil.getNearestComma(literal.getLastChild());
            if(trailingComma != null)
            {
                deleteList.add(trailingComma);
                trailingComma.delete();
            }
        }
        catch(Exception e)
        {
            System.out.println(e);
        }

        /*
        at first this block was not here and for some reason trailing commas in the function argument list
        were still deleted. I'm not sure why, but I decided to make it explicit.
         */
        try
        {
            PsiElement trailingComma = AMDPsiUtil.getNearestComma(function.getLastChild());
            if(trailingComma != null)
            {
                deleteList.add(trailingComma);
                trailingComma.delete();
            }
        }
        catch(Exception e)
        {
            System.out.println(e);
        }
    }

    public static void removeDefineLiteral(PsiElement element, Set<PsiElement> deleteList)
    {
        deleteList.add(element);

        // special case for when the element we're removing is last on the list
        PsiElement sibling = element.getNextSibling();
        if(sibling != null && (sibling instanceof PsiWhiteSpace || sibling.getText().equals("]")))
        {
            deleteList.add(AMDPsiUtil.getNearestComma(sibling));
        }

        // only remove the next sibling if it's a comma
        PsiElement nextSibling = element.getNextSibling();
        if(nextSibling != null && !nextSibling.getText().equals("]"))
        {
            deleteList.add(element.getNextSibling());
        }
    }

    public static void removeParameter(PsiElement element, Set<PsiElement> deleteList)
    {
        deleteList.add(element);

        PsiElement nextSibling = element.getNextSibling();

        // only remove commas at the end
        if(nextSibling != null && nextSibling.getText().equals(","))
        {
            deleteList.add(element.getNextSibling());
        }
    }

    public static void removeSingleImport(@NotNull AMDImport amdImport)
    {
        JSArrayLiteralExpression literal = (JSArrayLiteralExpression) amdImport.getLiteral().getParent();
        PsiElement function = amdImport.getParameter().getParent();

        Set<PsiElement> elementsToDelete = new LinkedHashSet<PsiElement>();

        removeParameter(amdImport.getParameter(), elementsToDelete);
        AMDPsiUtil.removeDefineLiteral(amdImport.getLiteral(), elementsToDelete);

        for(PsiElement element : elementsToDelete)
        {
            try
            {
                element.delete();
            }
            catch(Exception e)
            {
                // something happened, but it's probably not important when deleting.
            }
        }

        AMDPsiUtil.removeTrailingCommas(elementsToDelete, literal, function);
    }
}
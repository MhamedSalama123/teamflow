import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AiPanel } from './ai-panel';
import { AiService } from '../../core/ai/ai.service';
import { ProjectService } from '../../core/project/project.service';
import { GeneratedTask } from '../../core/ai/ai.models';

type Fn = ReturnType<typeof vi.fn>;

describe('AiPanel', () => {
  let aiStub: { summarize: Fn; generateTasks: Fn; ask: Fn };
  let projectStub: { createTask: Fn };

  function setup() {
    TestBed.configureTestingModule({
      imports: [AiPanel],
      providers: [
        { provide: AiService, useValue: aiStub },
        { provide: ProjectService, useValue: projectStub },
      ],
    });
    const fixture = TestBed.createComponent(AiPanel);
    fixture.componentRef.setInput('projectId', 1);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    aiStub = {
      summarize: vi.fn(() => of({ summary: 'A tidy summary.' })),
      generateTasks: vi.fn(() =>
        of({
          tasks: [
            { title: 'Design hero', priority: 'HIGH', dueDate: '2026-08-01' },
            { title: 'Write copy', priority: 'MEDIUM', dueDate: null },
          ] as GeneratedTask[],
        }),
      ),
      ask: vi.fn(() => of({ answer: 'The board is empty.' })),
    };
    projectStub = { createTask: vi.fn(() => of({ id: 99 })) };
  });

  it('summarizes on demand', () => {
    const component = setup().componentInstance as any;
    component.summarize();
    expect(aiStub.summarize).toHaveBeenCalledWith(1);
    expect(component.summary()).toBe('A tidy summary.');
  });

  it('generates tasks and adds one to the board', () => {
    const component = setup().componentInstance as any;
    component.description.set('build a landing page');
    component.generate();

    expect(aiStub.generateTasks).toHaveBeenCalledWith('build a landing page');
    expect(component.generatedTasks()).toHaveLength(2);

    component.addToBoard(component.generatedTasks()[0], 0);
    expect(projectStub.createTask).toHaveBeenCalledWith(1, {
      title: 'Design hero',
      priority: 'HIGH',
      dueDate: '2026-08-01',
    });
    expect(component.addedTasks().has(0)).toBe(true);
  });

  it('does not generate from a blank description', () => {
    const component = setup().componentInstance as any;
    component.description.set('   ');
    component.generate();
    expect(aiStub.generateTasks).not.toHaveBeenCalled();
  });

  it('answers a question', () => {
    const component = setup().componentInstance as any;
    component.question.set('what is left?');
    component.ask();
    expect(aiStub.ask).toHaveBeenCalledWith(1, 'what is left?');
    expect(component.answer()).toBe('The board is empty.');
  });
});
